# Module 12: External API Timeouts, Retries & Cascading Cascades

## Issue 12.1: The Infinite Timeout Default Trap, Cascading Failures, and Circuit Breakers

---

### 1. Scenario

During peak holiday volume on the **FinFlow Merchant POS & Checkout Platform**:
1. An external third-party Credit & Fraud Scoring Agency experiences severe network degradation: API responses slow down from **120 milliseconds to 45 seconds**.
2. Because the development team used Spring's default `RestClient` / `RestTemplate` instantiation without explicit timeout factories, **all 200 Tomcat worker threads freeze** waiting for TCP socket response bytes (`SocketInputStream.read()`).
3. Within 30 seconds, the entire FinFlow API gateway becomes unresponsive. Unrelated endpoints like `/api/v1/catalog`, `/api/v1/health`, and `/actuator/prometheus` fail with `504 Gateway Timeout` (Cascading Systemic Outage).
4. Automated retries across 10 microservice replicas fire simultaneously with zero backoff, generating a **Retry Storm** that delivers 50,000 requests/sec to the already crippled third-party agency.

---

### 2. Symptoms

```text
1. Socket Read Timeouts & Hung Threads:
   java.net.SocketTimeoutException: Read timed out
   org.springframework.web.client.ResourceAccessException: I/O error on POST request...
2. Web Server Worker Pool Saturation:
   Tomcat "http-nio-8080-exec-*" worker threads pinned at 100% capacity (200/200 active threads) in TIMED_WAITING or RUNNABLE state inside socketRead0().
3. Cascading Microservice Outage:
   Reverse proxies (Nginx/Cloudflare/AWS ALB) returning 504 Gateway Timeout across the entire domain.
4. Downstream Retry Storm Amplification:
   Downstream dependency experiences massive traffic multiplication due to non-jittered immediate retries.
5. High Resilience4j Metrics:
   resilience4j_circuitbreaker_state{name="creditAssessmentService", state="open"} == 1.
```

---

### 3. Possible Root Causes

1. **The "Infinite Timeout" Default in HTTP Clients:** Spring's default `RestTemplate`, `RestClient`, and JDK `HttpURLConnection` do not configure connect or read timeouts by default (timeout value is `-1` / infinite).
2. **Missing Circuit Breaker Protection:** Failing downstream calls are continually retried on every request instead of fast-failing once a failure threshold is breached.
3. **No Bulkhead Concurrency Isolation:** Uncapped concurrent requests to a single slow external dependency consume all available server threads, starving other internal services.
4. **Immediate Retries Without Jitter:** Retrying immediately without exponential backoff and randomized jitter concentrates load surges and guarantees downstream collapse.

---

### 4. Architecture Context: HTTP Client Timeout Layers & Circuit Breaker Lifecycle

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        EXTERNAL COMMUNICATION & RESILIENCE PIPELINE                    │
│                                                                                        │
│  [Client Request] ──► [Bulkhead (Max 2 Concurrent)] ──► [Circuit Breaker (Closed)]     │
│                                                                │                       │
│                                                       [Retry with Jitter]              │
│                                                                │                       │
│  ┌─────────────────────────────────────────────────────────────▼────────────────────┐  │
│  │                            HTTP CLIENT TIMEOUT ARCHITECTURE                      │  │
│  │                                                                                  │  │
│  │  1. Connect Timeout (500ms): TCP 3-Way Handshake (SYN ──► SYN-ACK ──► ACK)       │  │
│  │  2. TLS Handshake Timeout (500ms): Certificate exchange & session key agreement  │  │
│  │  3. Read / Socket Timeout (1000ms): Waiting for first response byte & completion  │  │
│  └────────────────────────────────────────┬─────────────────────────────────────────┘  │
│                                           │                                            │
│        Downstream Fails / Times Out?      ▼                                            │
│        ┌──────────────────────────────────────────────────────────────────────────┐    │
│        │ Failure Rate > 50% ──► Circuit Breaker Transitions to OPEN               │    │
│        │ Subsequent Calls   ──► Fast-Fail Instantly (< 1ms) to Fallback Method    │    │
│        │ Wait Duration (1s) ──► Transitions to HALF_OPEN to Probe Downstream      │    │
│        └──────────────────────────────────────────────────────────────────────────┘    │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### Step 1: Instantiate Default RestClient Without Timeout Configuration
```java
// ❌ ANTI-PATTERN: Default RestClient has NO read timeout!
RestClient unconfiguredClient = RestClient.builder()
    .baseUrl("https://unreliable-agency.com")
    .build();

// Hangs forever if remote server accepts TCP connection but never sends bytes!
unconfiguredClient.get().uri("/score").retrieve().body(String.class);
```

#### Step 2: Configure Client With Explicit Timeout Request Factory
```java
// ✅ PRODUCTION-GRADE: Explicit Connect and Read timeouts
SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
factory.setConnectTimeout(Duration.ofMillis(500));
factory.setReadTimeout(Duration.ofMillis(1000));

RestClient safeClient = RestClient.builder()
    .requestFactory(factory)
    .baseUrl("https://unreliable-agency.com")
    .build();
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Capture Thread Dump via `jstack`
Look for threads blocked inside socket reading:
```bash
jstack <PID> | grep -A 10 "SocketInputStream.read"
```
**Diagnostic Output:**
```text
"http-nio-8080-exec-18" #45 daemon prio=5 tid=0x00007f... nid=0x2b3c runnable
   java.lang.Thread.State: RUNNABLE
	at java.net.SocketInputStream.socketRead0(java.base@21.0.3/Native Method)
	at java.net.SocketInputStream.read(java.base@21.0.3/SocketInputStream.java:150)
	at sun.security.ssl.SSLSocketImpl.readRecord(java.base@21.0.3/SSLSocketImpl.java:1079)
	at org.springframework.http.client.SimpleClientHttpRequest.execute(SimpleClientHttpRequest.java:55)
	at com.finflow.troubleshooting.module12.client.ExternalCreditAgencyClient.assessCredit(ExternalCreditAgencyClient.java:35)
```

#### Method 2: Inspect Actuator Circuit Breaker Telemetry
Query `/actuator/circuitbreakers` and `/actuator/prometheus`:
```text
# HELP resilience4j_circuitbreaker_state State of the circuit breaker
# TYPE resilience4j_circuitbreaker_state gauge
resilience4j_circuitbreaker_state{name="creditAssessmentService",state="open"} 1.0
resilience4j_circuitbreaker_state{name="creditAssessmentService",state="closed"} 0.0

# HELP resilience4j_circuitbreaker_failure_rate Failure rate percentage
# TYPE resilience4j_circuitbreaker_failure_rate gauge
resilience4j_circuitbreaker_failure_rate{name="creditAssessmentService"} 100.0
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Inspect Thread Dumps for Hung Worker Threads.
        Verify if worker threads are accumulating in SocketInputStream.socketRead0.

Step 2: Enforce Explicit Connect & Read Timeouts.
        Configure SimpleClientHttpRequestFactory or JdkClientHttpRequestFactory on all RestClient/RestTemplate beans.

Step 3: Integrate Resilience4j Circuit Breaker & Fallback.
        Annotate service methods with @CircuitBreaker(name = "...", fallbackMethod = "...").
        Ensure the fallback method provides conservative default data (graceful degradation).

Step 4: Configure Bulkhead Concurrency Limits.
        Isolate downstream calls using @Bulkhead to ensure slow external dependencies can never consume more than
        a small fraction (e.g. 5-10%) of the web server's total thread pool.

Step 5: Configure Exponential Backoff with Jitter for Retries.
        Prevent retry storms by adding randomized delay intervals between retry attempts.
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. The Three Layers of Client Timeouts
1. **Connect Timeout:** The maximum time allowed to establish the initial TCP connection (TCP 3-way handshake SYN $\rightarrow$ SYN-ACK $\rightarrow$ ACK). If a remote firewall silently drops packets, this timeout prevents the thread from waiting the OS default TCP timeout (75 to 120 seconds).
2. **TLS / SSL Handshake Timeout:** Time allowed to perform cryptographic certificate verification and key exchange.
3. **Read / Socket Timeout (`SO_TIMEOUT`):** The maximum time allowed between consecutive incoming data packets once the connection is established. If a server accepts a request but freezes before writing response bytes, `SO_TIMEOUT` unblocks the thread.

#### 2. The Mathematics of Retry Storms & Full Jitter
If 1,000 concurrent users encounter a transient failure and the client retries immediately with fixed delay ($D$), all 1,000 clients retry at the exact same millisecond, creating destructive harmonic load spikes.

**Full Jitter Formula:**
$$T_{\text{sleep}} = \text{random}\left(0, \, \min(T_{\text{max}}, \, T_{\text{base}} \times 2^{\text{attempt}})\right)$$
Randomizing the backoff smooths out client traffic into a flat, manageable distribution.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Resilient Client Configuration with Explicit Timeouts
```java
@Configuration
public class RestClientConfig {

    @Bean
    public RestClient externalCreditAgencyRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(500)); // 500ms Connect Timeout
        factory.setReadTimeout(Duration.ofMillis(1000));   // 1000ms Read Timeout

        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl("https://credit-agency.finflow.com")
                .build();
    }
}
```

#### ✅ Fix 2: Production Resilience4j Configuration in `application.yml`
```yaml
resilience4j:
  circuitbreaker:
    instances:
      creditAssessmentService:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 20
        minimumNumberOfCalls: 10
        failureRateThreshold: 50.0 # Open circuit if >= 50% of calls fail
        waitDurationInOpenState: 5000ms # Stay OPEN for 5s before probing
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
  retry:
    instances:
      creditAssessmentService:
        maxAttempts: 3
        waitDuration: 100ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2.0
        retryExceptions:
          - org.springframework.web.client.ResourceAccessException
          - java.io.IOException
  bulkhead:
    instances:
      creditAssessmentService:
        maxConcurrentCalls: 15 # Never allow > 15 threads to call credit agency simultaneously
        maxWaitDuration: 50ms
```

#### ✅ Fix 3: Multi-Layered Service with Fallback Degradation
```java
@Service
public class CreditAssessmentService {

    private final ExternalCreditAgencyClient agencyClient;

    public CreditAssessmentService(ExternalCreditAgencyClient agencyClient) {
        this.agencyClient = agencyClient;
    }

    @CircuitBreaker(name = "creditAssessmentService", fallbackMethod = "fallbackAssessCredit")
    @Retry(name = "creditAssessmentService")
    @Bulkhead(name = "creditAssessmentService")
    public CreditAssessmentResult evaluateCredit(String customerId, boolean simulateFailure, boolean simulateTimeout) {
        return agencyClient.assessCredit(customerId, simulateFailure, simulateTimeout);
    }

    // Graceful degradation fallback
    public CreditAssessmentResult fallbackAssessCredit(String customerId, boolean simulateFailure,
                                                       boolean simulateTimeout, Throwable ex) {
        return new CreditAssessmentResult(customerId, 600, "MANUAL_REVIEW_FALLBACK", true);
    }
}
```

---

### 10. Verification

1. **Timeout Configuration Test:** Run `ClientTimeoutConfigurationTest.java` to verify that slow responses throw `ResourceAccessException` immediately after the configured read timeout.
2. **Circuit Breaker Transition Test:** Run `CircuitBreakerStateTransitionTest.java` to verify the circuit breaker transitions from `CLOSED` to `OPEN` on repeated downstream failures and fast-fails directly to the fallback.
3. **Bulkhead Concurrency Test:** Run `BulkheadIsolationTest.java` to verify concurrent thread safety under constrained bulkhead limits.
4. **Integration Test:** Run `Module12IntegrationTest.java` to verify normal and fallback REST endpoints.

---

### 11. Prevention & Production Readiness

1. **Enforce Client Timeout Audits in CI/CD:**
   Use ArchUnit rules to block PRs creating `new RestTemplate()` or `RestClient.builder()` without an explicit `ClientHttpRequestFactory` timeout definition.
2. **Define Fallbacks for All Non-Critical External Dependencies:**
   Identify non-critical calls (e.g. recommendations, loyalty points, analytics) and provide instantaneous cached or default fallbacks.
3. **Chaos Testing (Network Latency Injection):**
   Use Toxiproxy or Chaos Mesh in staging environments to inject 10-second delays on third-party HTTP dependencies and ensure zero worker thread pool exhaustion.

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What is the difference between a Connect Timeout, a Read Timeout, and a TLS Handshake Timeout?**
2. **Q: How does a Count-Based sliding window differ from a Time-Based sliding window in Resilience4j?**
3. **Q: What is the purpose of the `HALF_OPEN` state in a Circuit Breaker?**
4. **Q: Why are immediate retries without exponential backoff and jitter dangerous in distributed systems?**
5. **Q: How does Bulkhead isolation protect an application from cascading thread pool exhaustion?**

#### Production Incident Questions
1. **Incident:** An external notification service went down. Within 2 minutes, your core payment processing engine stopped responding. How did unconfigured read timeouts cause this cascade?
2. **Incident:** A circuit breaker opened during a network blip. After the remote service recovered, the circuit remained stuck in `OPEN` state. What configuration parameters govern recovery?
3. **Incident:** You configured `@Retry` and `@CircuitBreaker` on the same method. Under failure, your retry executes 3 times for every single user request. How does retry count affect circuit breaker failure rate calculations?
4. **Incident:** A REST client using connection pooling throws `org.apache.http.conn.ConnectionPoolTimeoutException: Timeout waiting for connection from pool`. How do you distinguish client-side HTTP pool starvation from server-side saturation?
5. **Incident:** How do you propagate distributed trace IDs (`traceparent` header) across asynchronous HTTP clients (`WebClient` / `RestClient`)?

#### Trick Questions
1. **Trick:** If a method annotated with `@CircuitBreaker` throws an exception, but the exception is handled by a `fallbackMethod`, what HTTP status code does the controller return by default?
2. **Trick:** If you configure `maxAttempts: 3` on `@Retry`, will the method execute up to 3 times total or 4 times total (1 initial + 3 retries)?
3. **Trick:** Does a circuit breaker in `OPEN` state make any network calls to the downstream service?

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
