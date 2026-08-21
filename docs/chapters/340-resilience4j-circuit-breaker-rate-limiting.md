---
chapter: 340
topic: Resilience4j — Circuit Breaker, Retry, Rate Limiter, Bulkhead, TimeLimiter
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270, 280, 290, 300, 310, 320, 330]
reference_system_node: Payment Service ↔ Third-Party Payment Gateway (Stripe-like API / Fraud Provider)
---

# Chapter 340: Resilience4j — Circuit Breaker, Retry, Rate Limiter, Bulkhead, TimeLimiter

## 1. Concept

In distributed architectures, transient network glitches, downstream database freezes, and third-party API outages are inevitable physical realities. When a downstream service degrades (e.g. latency climbs from 50ms to 45s), an unshielded upstream service will continue flooding it with requests. 

This causes **cascading system failure**:
1. Inbound HTTP requests block on slow downstream sockets.
2. The application server's thread pool (e.g. Tomcat's default 200 worker threads) becomes 100% saturated.
3. Incoming requests for completely unrelated, healthy endpoints (such as `/actuator/health` or internal read caches) get queued and rejected.
4. Kubernetes liveness probes fail because the health endpoint cannot acquire a thread within its 3-second timeout.
5. Kubernetes restarts the entire fleet, destroying the cluster in a catastrophic restart storm.

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                              The Cascading Failure Death Spiral                                 │
│                                                                                                 │
│  [Third-Party Gateway] ──► Latency spikes from 50ms to 45,000ms (Database Lockout)             │
│            │                                                                                    │
│            ▼                                                                                    │
│  [Payment Service]     ──► 200/200 Tomcat threads block in SocketInputStream.socketRead0()      │
│            │                                                                                    │
│            ▼                                                                                    │
│  [Health Probe]        ──► K8s kubelet GET /actuator/health blocked in Tomcat accept queue      │
│            │                                                                                    │
│            ▼                                                                                    │
│  [Cluster Meltdown]    ──► K8s restarts 20 pods simultaneously; incoming traffic causes        │
│                            instant OOM and CrashLoopBackOff during JVM warm-up.                 │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

### The Resilience4j Ecosystem

**Resilience4j** is a lightweight, fault tolerance library designed for Java 17/21 and Spring Boot 3. Unlike legacy libraries like Netflix Hystrix (which relied on heavyweight Archaius configuration, RxJava thread pools, and is now deprecated and archived), Resilience4j is built on **functional programming principles (decorators)** and high-performance concurrency primitives.

Resilience4j provides 5 modular decorators that compose seamlessly:

| Decorator | Core Responsibility | Failure Mode Prevented |
|---|---|---|
| **CircuitBreaker** | Stops invoking an unhealthy downstream service when failure or slow-call rates cross configured thresholds. Fast-fails immediately. | Cascading downtime & thread starvation. |
| **Retry** | Automatically re-attempts failed operations for transient errors with exponential backoff and jitter. | Transient network blips & socket timeouts. |
| **RateLimiter** | Enforces maximum executions within a sliding time window (token/epoch pacing). | Downstream API quota exhaustion (HTTP 429). |
| **Bulkhead** | Limits concurrent executions targeting a specific resource (Semaphore or isolated ThreadPool). | Global thread pool exhaustion from one bad route. |
| **TimeLimiter** | Sets an upper bound on async execution duration, canceling hanging futures. | Unbounded latency accumulation. |

---

## 2. Internal Working

### 2.1 The CircuitBreaker State Machine

Resilience4j's `CircuitBreaker` is an atomic, state-driven finite state machine managed via `AtomicReference<State>` and lock-free concurrency.

```
                         ┌───────────────────────────┐
                         │                           │
                         │          CLOSED           │ ◄───────────────────────────┐
                         │  (Normal Operation: 100%  │                             │
                         │   Traffic Permitted)      │                             │
                         │                           │                             │
                         └─────────────┬─────────────┘                             │
                                       │ Failure Rate > 50%                        │
                                       │ or Slow Call Rate > 50%                   │
                                       ▼                                           │
                         ┌───────────────────────────┐                             │
                         │                           │                             │
                         │           OPEN            │                             │ Success Rate >= 50%
                         │  (Fast-Fail: 100% Traffic │                             │ in Half-Open probe
                         │   Rejected Immediately)   │                             │
                         │                           │                             │
                         └─────────────┬─────────────┘                             │
                                       │ waitDurationInOpenState (e.g. 5000ms)    │
                                       │ elapses                                   │
                                       ▼                                           │
                         ┌───────────────────────────┐                             │
                         │                           │                             │
                         │         HALF_OPEN         │ ────────────────────────────┘
                         │ (Test Probe: Permitted    │
                         │  N Calls Evaluated)       │ ────────────────────────────┐
                         │                           │                             │
                         └───────────────────────────┘                             │ Failure Rate > 50%
                                       ▲                                           │ in Half-Open probe
                                       └───────────────────────────────────────────┘
```

#### State Definitions:
1. **CLOSED**: Downstream calls execute normally. Call outcomes (success, failure, slow call) are recorded in a sliding window.
2. **OPEN**: When failure rate or slow call rate exceeds configured percentages over the minimum sample size, the circuit trips to `OPEN`. All incoming calls fast-fail immediately by throwing `CallNotPermittedException` without touching the downstream network.
3. **HALF_OPEN**: After `waitDurationInOpenState` expires, the state machine enters `HALF_OPEN`. A configurable number of trial calls (`permittedNumberOfCallsInHalfOpenState`) are allowed through. If these trial calls succeed above threshold, the state transitions back to `CLOSED`. If they fail, it transitions back to `OPEN` for another wait period.
4. **DISABLED / FORCED_OPEN**: Manual override states for operational maintenance or emergency isolation.

---

### 2.2 Sliding Window Implementations

Resilience4j evaluates metrics using two sliding window algorithms:

#### A. Count-Based Sliding Window (`COUNT_BASED`)
Maintains a circular array of size $N$ (e.g., $N = 100$ calls). Each slot stores the outcome of 1 execution.
- Bitmask aggregation calculates failures, slow calls, and successes with $O(1)$ memory and lock-free CAS updates.
- Threshold evaluation only activates once `minimumNumberOfCalls` (e.g. 10) have been recorded.

$$\text{Failure Rate \%} = \left(\frac{\text{Number of Failed Calls}}{\text{Number of Buffered Calls}}\right) \times 100$$

$$\text{Slow Call Rate \%} = \left(\frac{\text{Number of Calls with Latency} \ge \text{slowCallDurationThreshold}}{\text{Number of Buffered Calls}}\right) \times 100$$

#### B. Time-Based Sliding Window (`TIME_BASED`)
Maintains a circular array of $N$ time buckets (e.g., 60 buckets of 1 second each for a 60-second window).
- Each 1-second bucket aggregates the total count of success, failure, and slow calls that occurred within that epoch second.
- Provides precise rolling window metrics over variable throughput environments.

---

### 2.3 Aspect Composition & Interceptor Order

When combining multiple annotations (`@CircuitBreaker`, `@Retry`, `@RateLimiter`, `@Bulkhead`, `@TimeLimiter`) on the same Spring bean method, Spring AOP weaves an interceptor chain. 

The order of execution is critical to system correctness. Resilience4j defines the standard default aspect order (from outermost to innermost):

```
Inbound Request
       │
       ▼
┌────────────────────────────────────────────────────────┐
│ 1. Retry Aspect (Highest Precedence)                  │
│    Wraps all inner layers. If an error occurs, it     │
│    retries the entire inner stack.                     │
└───────────────────────┬────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────┐
│ 2. CircuitBreaker Aspect                               │
│    Checks if circuit is OPEN. If OPEN, fast-fails      │
│    before RateLimiter or Bulkhead consume resources.   │
└───────────────────────┬────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────┐
│ 3. RateLimiter Aspect                                  │
│    Enforces tokens/sec. Rejects with HTTP 429 if       │
│    out of permits.                                     │
└───────────────────────┬────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────┐
│ 4. TimeLimiter Aspect                                  │
│    Guards execution time of asynchronous calls.        │
└───────────────────────┬────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────┐
│ 5. Bulkhead Aspect (Lowest Precedence)                 │
│    Acquires concurrency permit immediately before      │
│    invoking the business method.                       │
└───────────────────────┬────────────────────────────────┘
                        │
                        ▼
┌────────────────────────────────────────────────────────┐
│ 6. Target Business Method (e.g. Stripe Gateway HTTP)  │
└────────────────────────────────────────────────────────┘
```

> [!IMPORTANT]
> **Why Order Matters:**
> If `@Retry` is placed *inside* `@CircuitBreaker`, a single failed client call that retries 3 times will count as 3 separate failures in the CircuitBreaker ring buffer, tripping the circuit 3x faster than intended. Conversely, with `@Retry` on the *outside*, each retry attempts to pass through the CircuitBreaker; if the Circuit trips to `OPEN` on attempt 1, attempts 2 and 3 fail immediately via `CallNotPermittedException` without wasting downstream network sockets.

---

### 2.4 RateLimiter Mechanics (`AtomicRateLimiter`)

Resilience4j's default `AtomicRateLimiter` splits time into cycles (nanoseconds). It stores state in a single `AtomicReference` holding:
1. `cycle`: The current cycle number.
2. `activePermissions`: Number of available tokens in the current cycle.
3. `nanosToWait`: Nanoseconds a thread must sleep if tokens are momentarily exhausted.

When a thread requests a permit:
- It performs a CAS loop calculating whether tokens exist in the current period.
- If tokens exist, `activePermissions` is decremented.
- If exhausted, and `timeoutDuration > 0`, the thread parks for `nanosToWait`. If timeout is exceeded, it rejects with `RequestNotPermitted`.

---

## 3. Enterprise Scenario: FinFlow 12-Hop Distributed Checkout Flow

In the FinFlow payment ecosystem, the **Payment Service** communicates with an external banking partner (`StripeGateway`) to authorize credit card charges.

```
Traffic Baseline (illustrative):
- Total Platform Ingress: ~4,000 req/sec peak
- Active Payment Pods: 20 pods (each pod handles ~200 req/sec)
- Tomcat Max Threads per pod: 200 worker threads
- Nominal Stripe Gateway P99: 120ms
- SLA Target: 99.99% availability, P99 < 500ms
```

### The Incident Trigger
At 14:00:00 UTC, the third-party payment partner suffers a database deadlock during their maintenance window. Their load balancer accepts TCP handshakes, but HTTP response bodies stall indefinitely (hanging for 45+ seconds before TCP RST).

---

## 4. Incorrect Implementation

Below is the naive implementation typical of unshielded production services:

```java
package com.finflow.chapter340.incorrect;

import org.springframework.http.ResponseEntity;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Service
public class UnshieldedPaymentService {

    private final RestTemplate restTemplate;

    public UnshieldedPaymentService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate; // Default RestTemplate: Infinite connect/read timeout!
    }

    /**
     * FLUSH WITH CATASTROPHIC MISTAKES:
     * 1. Infinite or 60-second read timeout on RestTemplate.
     * 2. @Retryable retries 5 times without exponential backoff or jitter.
     * 3. Retries ALL exceptions (including 400 Bad Request, 402 Insufficient Funds).
     * 4. No Circuit Breaker — continuously hammers dead downstream.
     * 5. No Bulkhead — allows slow gateway calls to consume all 200 Tomcat worker threads.
     */
    @Retryable(
        retryFor = { Exception.class },
        maxAttempts = 5,
        backoff = @Backoff(delay = 100)
    )
    public String processPayment(String txId, BigDecimal amount) {
        String gatewayUrl = "https://api.stripe-mock.finflow.internal/v1/charges";
        
        // Blocking HTTP call — locks thread for 60 seconds per attempt
        ResponseEntity<Map> response = restTemplate.postForEntity(
            gatewayUrl,
            Map.of("transactionId", txId, "amount", amount),
            Map.class
        );

        return (String) response.getBody().get("charge_id");
    }
}
```

---

## 5. Production Incident

```
INCIDENT REPORT: INC-88219
Severity: SEV-1 (Platform-Wide Critical Outage)
Impact: 100% of checkout payments failed; 48,000 users blocked; $1,240,000 (illustrative) in uncaptured revenue.
Duration: 22 minutes
```

### Incident Timeline

| Time (UTC) | Event |
|---|---|
| **14:00:00** | Upstream payment partner encounters database lockup; average latency spikes from 65ms to 45,000ms. |
| **14:00:15** | FinFlow Payment Service pods experience thread buildup: 200 req/sec $\times$ 45s wait time $\gg$ 200 Tomcat threads. |
| **14:00:25** | All 200 Tomcat worker threads on all 20 pods enter `WAITING` state inside `SocketInputStream.socketRead0()`. |
| **14:00:30** | Incoming user requests queue in OS TCP backlog (`acceptCount=100`) and get dropped with `Connection Refused` / HTTP 504. |
| **14:00:45** | Kubernetes Kubelet attempts `GET /actuator/health` liveness probe. Probe fails with `HTTP 500 Connection Timeout` because Tomcat has no free thread to dispatch the request. |
| **14:01:00** | Kubernetes marks all 20 pods unhealthy and initiates rolling container restarts. |
| **14:01:30** | Restarting pods boot up; during JVM C2 JIT warm-up, 4,000 req/sec instantly hits them. Pods crash with Out-Of-Memory and CPU throttle. Entire deployment enters `CrashLoopBackOff`. |
| **14:15:00** | SRE isolates traffic, enables Resilience4j circuit breaking with fast-fail fallback, and restarts the fleet. |
| **14:22:00** | System recovers; circuit breaker absorbs downstream outage with 0ms fast-fail async queueing. |

---

## 6. Logs & Diagnostics

### Application Logs during Thread Starvation
```text
2026-08-21T14:00:28.140+00:00 ERROR [payment-service,7a8b9c0d1e2f3a4b,9f8e7d6c5b4a3a2b] 18420 --- [payment-service] [http-nio-8080-exec-198] o.a.c.c.C.[.[.[/].[dispatcherServlet] : Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception
java.lang.IllegalStateException: Resource pool exhausted: No threads available in thread pool [http-nio-8080-exec] (Active: 200/200, Queue: 100/100)
    at org.apache.tomcat.util.threads.ThreadPoolExecutor.execute(ThreadPoolExecutor.java:175)
    at org.apache.coyote.http11.Http11NioProtocol.process(Http11NioProtocol.java:65)
    at org.apache.tomcat.util.net.NioEndpoint$SocketProcessor.doRun(NioEndpoint.java:1782)

2026-08-21T14:00:32.451+00:00 WARN  [payment-service,,] 18420 --- [payment-service] [http-nio-8080-Acceptor] o.apache.tomcat.util.net.Acceptor       : Socket accept failed
java.io.IOException: Socket accept backlog full (acceptCount=100)
    at java.base/sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:312)
```

### JVM Thread Dump Excerpt (200 Threads Hanging in Socket Read)
```text
"http-nio-8080-exec-142" #185 daemon prio=5 os_prio=0 cpu=1240.21ms elapsed=42.11s tid=0x00007f9c88014a00 nid=0x48f2 runnable  [0x00007f9c314de000]
   java.lang.Thread.State: RUNNABLE
    at java.base/sun.nio.ch.SocketDispatcher.read0(Native Method)
    at java.base/sun.nio.ch.SocketDispatcher.read(SocketDispatcher.java:47)
    at java.base/sun.nio.ch.NioSocketImpl.tryRead(NioSocketImpl.java:256)
    at java.base/sun.nio.ch.NioSocketImpl.implRead(NioSocketImpl.java:307)
    at java.base/sun.nio.ch.NioSocketImpl.read(NioSocketImpl.java:346)
    at java.base/sun.nio.ch.NioSocketImpl$1.read(NioSocketImpl.java:796)
    at java.base/java.net.Socket$SocketInputStream.read(Socket.java:1099)
    at org.apache.http.impl.io.SessionInputBufferImpl.fillBuffer(SessionInputBufferImpl.java:151)
    at org.apache.http.impl.io.SessionInputBufferImpl.readLine(SessionInputBufferImpl.java:284)
    at org.apache.http.impl.conn.DefaultHttpResponseParser.parseHead(DefaultHttpResponseParser.java:138)
    at org.springframework.web.client.RestTemplate.doExecute(RestTemplate.java:889)
    at com.finflow.chapter340.incorrect.UnshieldedPaymentService.processPayment(UnshieldedPaymentService.java:34)
```

---

## 7. Root Cause Analysis

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                    Root Cause Mechanism                                         │
│                                                                                                 │
│  1. Unbounded Latency Coupling: Without a TimeLimiter or socket timeout, the upstream thread   │
│     lifetime is 100% bound to the downstream response duration (45,000ms).                      │
│                                                                                                 │
│  2. Mathematical Exhaustion: At 200 req/sec per pod, 200 Tomcat threads are exhausted in       │
│     exactly 1.0 second:                                                                         │
│                          Time to Saturation = 200 threads / 200 req/sec = 1.0 sec               │
│                                                                                                 │
│  3. Naive Retry Multiplier: Retrying 5 times turned 4,000 inbound requests into 20,000          │
│     downstream requests, worsening the partner's database lock contention.                      │
│                                                                                                 │
│  4. Probe Fate-Sharing: Kubernetes health probes shared the same saturated thread pool as       │
│     business transactions, causing unnecessary cascading container kills.                       │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 8. Debugging Process

When alerted by PagerDuty for `HighPaymentFailureRate` and `PodRestartStorm`:

### Step 1: Check Pod Health & Restart Counts
```bash
kubectl get pods -n finflow -l app=payment-service
```
*Output reveals pods restarting repeatedly with `CrashLoopBackOff` or `OOMKilled`.*

### Step 2: Inspect Actuator Metrics & Prometheus
Check active Tomcat threads and CircuitBreaker metrics:
```bash
curl -s http://localhost:8080/actuator/metrics/tomcat.threads.busy
curl -s http://localhost:8080/actuator/metrics/resilience4j.circuitbreaker.state
```

### Step 3: Trigger Live Thread Dump
```bash
jcmd $(pgrep -f payment-service) Thread.print > /tmp/threaddump.txt
grep -c "SocketInputStream.socketRead0" /tmp/threaddump.txt
```
*If >150 threads are stuck in socket reads, the service is suffering from downstream latency starvation.*

### Step 4: Verify CircuitBreaker Events Endpoint
```bash
curl -s http://localhost:8080/actuator/circuitbreakerevents/stripeGateway
```
*Shows state transition timestamps and failure rate calculations.*

---

## 9. Correct Implementation

### 9.1 Maven Configuration (`pom.xml`)

```xml
<!-- Resilience4j Spring Boot 3 Starter -->
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-micrometer</artifactId>
    <version>2.2.0</version>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-aop</artifactId>
</dependency>
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

---

### 9.2 Complete Resilience4j Configuration (`application.yml`)

```yaml
server:
  port: 8080

spring:
  application:
    name: payment-service

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus,circuitbreakers,circuitbreakerevents,retries,ratelimiters,bulkheads
  endpoint:
    health:
      show-details: always
    circuitbreakers:
      enabled: true
    circuitbreakerevents:
      enabled: true
    retries:
      enabled: true
    ratelimiters:
      enabled: true
    bulkheads:
      enabled: true

resilience4j:
  circuitbreaker:
    configs:
      default:
        slidingWindowType: COUNT_BASED
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        failureRateThreshold: 50.0
        slowCallRateThreshold: 50.0
        slowCallDurationThreshold: 2000ms
        waitDurationInOpenState: 5000ms
        permittedNumberOfCallsInHalfOpenState: 3
        automaticTransitionFromOpenToHalfOpenEnabled: true
        recordExceptions:
          - com.finflow.chapter340.exception.GatewayTimeoutException
          - com.finflow.chapter340.exception.GatewayServiceUnavailableException
          - java.io.IOException
          - java.util.concurrent.TimeoutException
        ignoreExceptions:
          - com.finflow.chapter340.exception.PaymentValidationException
    instances:
      stripeGateway:
        baseConfig: default

  retry:
    configs:
      default:
        maxAttempts: 3
        waitDuration: 100ms
        enableExponentialBackoff: true
        exponentialBackoffMultiplier: 2
        retryExceptions:
          - com.finflow.chapter340.exception.GatewayTimeoutException
          - com.finflow.chapter340.exception.GatewayServiceUnavailableException
          - java.io.IOException
        ignoreExceptions:
          - com.finflow.chapter340.exception.PaymentValidationException
    instances:
      stripeGateway:
        baseConfig: default

  ratelimiter:
    configs:
      default:
        limitForPeriod: 50
        limitRefreshPeriod: 1s
        timeoutDuration: 20ms
    instances:
      stripeGateway:
        limitForPeriod: 10
        limitRefreshPeriod: 1s
        timeoutDuration: 50ms

  bulkhead:
    configs:
      default:
        maxConcurrentCalls: 10
        maxWaitDuration: 20ms
    instances:
      stripeGateway:
        maxConcurrentCalls: 5
        maxWaitDuration: 10ms

  timelimiter:
    configs:
      default:
        timeoutDuration: 3000ms
        cancelRunningFuture: true
    instances:
      stripeGateway:
        baseConfig: default
```

---

### 9.3 The Resilient Service Layer (`ResilientPaymentService.java`)

```java
package com.finflow.chapter340.service;

import com.finflow.chapter340.client.PaymentGatewayClient;
import com.finflow.chapter340.domain.PaymentRequest;
import com.finflow.chapter340.domain.PaymentResponse;
import com.finflow.chapter340.domain.PaymentStatus;
import com.finflow.chapter340.exception.GatewayServiceUnavailableException;
import com.finflow.chapter340.exception.GatewayTimeoutException;
import com.finflow.chapter340.exception.PaymentValidationException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker as R4jCircuitBreaker;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ResilientPaymentService {

    private static final Logger log = LoggerFactory.getLogger(ResilientPaymentService.class);
    private static final String INSTANCE_NAME = "stripeGateway";

    private final PaymentGatewayClient gatewayClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public ResilientPaymentService(PaymentGatewayClient gatewayClient, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.gatewayClient = gatewayClient;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    /**
     * Executes payment with complete multi-layered resilience:
     * Retry -> CircuitBreaker -> RateLimiter -> Bulkhead -> Downstream.
     */
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(
            name = INSTANCE_NAME,
            fallbackMethod = "handleCircuitBreakerOrServerError"
    )
    @Retry(name = INSTANCE_NAME, fallbackMethod = "handleCircuitBreakerOrServerError")
    @RateLimiter(name = INSTANCE_NAME, fallbackMethod = "handleRateLimitError")
    @Bulkhead(name = INSTANCE_NAME, fallbackMethod = "handleBulkheadError")
    public PaymentResponse processPayment(PaymentRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("[ResilientPaymentService] Processing payment tx: {}, merchant: {}, amount: {}",
                request.getTransactionId(), request.getMerchantId(), request.getAmount());

        String gatewayRef = gatewayClient.executePayment(request);
        long duration = System.currentTimeMillis() - startTime;

        String cbState = getCircuitBreakerState();
        return PaymentResponse.success(request.getTransactionId(), gatewayRef, duration, cbState);
    }

    /**
     * Fallback 1: Fast-fail when Circuit Breaker is OPEN.
     * Takes 0ms, zero network sockets, routes to async background settlement queue.
     */
    public PaymentResponse handleCircuitBreakerOrServerError(PaymentRequest request, CallNotPermittedException ex) {
        log.warn("[ResilientPaymentService] Circuit Breaker OPEN fast-fail for tx: {}. Message: {}",
                request.getTransactionId(), ex.getMessage());
        return PaymentResponse.fallback(
                request.getTransactionId(),
                "Payment accepted for asynchronous background settlement (Circuit Breaker OPEN fast-fail)",
                0,
                CircuitBreaker.State.OPEN.name()
        );
    }

    /**
     * Fallback 2: Downstream Gateway Timeout after retries exhausted.
     */
    public PaymentResponse handleCircuitBreakerOrServerError(PaymentRequest request, GatewayTimeoutException ex) {
        log.warn("[ResilientPaymentService] Gateway timeout after retries for tx: {}. Routing to async fallback.",
                request.getTransactionId());
        return PaymentResponse.fallback(
                request.getTransactionId(),
                "Payment timed out with gateway. Enqueued in reliable dead-letter/async reconciliation queue.",
                0,
                getCircuitBreakerState()
        );
    }

    /**
     * Fallback 3: Downstream HTTP 503 / 500 errors after retries exhausted.
     */
    public PaymentResponse handleCircuitBreakerOrServerError(PaymentRequest request, GatewayServiceUnavailableException ex) {
        log.warn("[ResilientPaymentService] Gateway unavailable after retries for tx: {}. Routing to async fallback.",
                request.getTransactionId());
        return PaymentResponse.fallback(
                request.getTransactionId(),
                "Payment gateway 503 unavailable. Enqueued for reliable async retry worker.",
                0,
                getCircuitBreakerState()
        );
    }

    /**
     * Fallback 4: General catch-all. Never catches validation errors (rethrows them).
     */
    public PaymentResponse handleCircuitBreakerOrServerError(PaymentRequest request, Throwable ex) {
        if (ex instanceof PaymentValidationException) {
            log.error("[ResilientPaymentService] Validation error for tx: {}. Will not fallback.", request.getTransactionId());
            throw (PaymentValidationException) ex;
        }
        log.error("[ResilientPaymentService] Unhandled error during payment execution for tx: {}. Root: {}",
                request.getTransactionId(), ex.getMessage());
        return PaymentResponse.fallback(
                request.getTransactionId(),
                "Payment processing encountered error: " + ex.getMessage() + ". Enqueued to fallback recovery.",
                0,
                getCircuitBreakerState()
        );
    }

    /**
     * Fallback 5: Rate Limiter capacity exceeded (HTTP 429 backoff).
     */
    public PaymentResponse handleRateLimitError(PaymentRequest request, RequestNotPermitted ex) {
        log.warn("[ResilientPaymentService] Rate limit exceeded for tx: {}. Request rejected.",
                request.getTransactionId());
        return PaymentResponse.rateLimited(
                request.getTransactionId(),
                "Rate limit exceeded (HTTP 429 Too Many Requests). Please back off and retry.",
                getCircuitBreakerState()
        );
    }

    /**
     * Fallback 6: Bulkhead capacity exceeded (Concurrency limit reached).
     */
    public PaymentResponse handleBulkheadError(PaymentRequest request, BulkheadFullException ex) {
        log.warn("[ResilientPaymentService] Bulkhead full for tx: {}. Concurrency limit reached.",
                request.getTransactionId());
        return new PaymentResponse(
                request.getTransactionId(),
                PaymentStatus.FAILED,
                null,
                0,
                "Concurrent gateway call limit reached (Bulkhead Full). Backing off.",
                getCircuitBreakerState()
        );
    }

    public String getCircuitBreakerState() {
        return circuitBreakerRegistry.circuitBreaker(INSTANCE_NAME).getState().name();
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker(INSTANCE_NAME);
    }
}
```

---

### 9.4 Real-Time CircuitBreaker Event Tracker (`CircuitBreakerEventTracker.java`)

```java
package com.finflow.chapter340.listener;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;

@Component
public class CircuitBreakerEventTracker {

    private static final Logger log = LoggerFactory.getLogger(CircuitBreakerEventTracker.class);

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;
    private final RateLimiterRegistry rateLimiterRegistry;

    private final List<String> eventLogs = new CopyOnWriteArrayList<>();

    public CircuitBreakerEventTracker(CircuitBreakerRegistry circuitBreakerRegistry,
                                      RetryRegistry retryRegistry,
                                      RateLimiterRegistry rateLimiterRegistry) {
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.retryRegistry = retryRegistry;
        this.rateLimiterRegistry = rateLimiterRegistry;
    }

    @PostConstruct
    public void registerEventListeners() {
        circuitBreakerRegistry.getAllCircuitBreakers().forEach(this::bindCircuitBreakerEvents);
        circuitBreakerRegistry.getEventPublisher().onEntryAdded(event -> bindCircuitBreakerEvents(event.getAddedEntry()));

        retryRegistry.getAllRetries().forEach(retry -> {
            retry.getEventPublisher()
                    .onRetry(e -> recordEvent("[RETRY-ATTEMPT] Instance: " + retry.getName() + " | Attempt: " + e.getNumberOfRetryAttempts()))
                    .onError(e -> recordEvent("[RETRY-EXHAUSTED] Instance: " + retry.getName() + " | Last error: " + e.getLastThrowable().getMessage()));
        });

        rateLimiterRegistry.getAllRateLimiters().forEach(rl -> {
            rl.getEventPublisher()
                    .onFailure(e -> recordEvent("[RATE-LIMIT-REJECTED] Instance: " + rl.getName() + " | Limit exceeded"));
        });
    }

    private void bindCircuitBreakerEvents(CircuitBreaker cb) {
        cb.getEventPublisher()
                .onStateTransition(event -> {
                    String logMsg = String.format("[CIRCUIT-BREAKER-TRANSITION] %s transitioned from %s to %s",
                            event.getCircuitBreakerName(),
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState());
                    log.warn(logMsg);
                    recordEvent(logMsg);
                })
                .onError(event -> {
                    String logMsg = String.format("[CIRCUIT-BREAKER-ERROR] %s recorded error: %s (Duration: %dms)",
                            event.getCircuitBreakerName(),
                            event.getThrowable().getClass().getSimpleName(),
                            event.getElapsedDuration().toMillis());
                    log.error(logMsg);
                    recordEvent(logMsg);
                })
                .onCallNotPermitted(event -> {
                    String logMsg = String.format("[CIRCUIT-BREAKER-FAST-FAIL] %s call not permitted (Circuit is OPEN)",
                            event.getCircuitBreakerName());
                    log.warn(logMsg);
                    recordEvent(logMsg);
                });
    }

    private void recordEvent(String event) {
        eventLogs.add(event);
        if (eventLogs.size() > 500) {
            eventLogs.remove(0);
        }
    }

    public List<String> getEventLogs() {
        return List.copyOf(eventLogs);
    }

    public void clearEventLogs() {
        eventLogs.clear();
    }
}
```

---

## 10. Performance Comparison

The table below illustrates system behavior under a 100% downstream gateway outage (~4,000 req/sec total ingress, 20 pods):

| Metric | Without Resilience4j (Unshielded) | With Resilience4j (Shielded) | Production Impact |
|---|---|---|---|
| **P50 Latency under Outage** | 45,000ms (illustrative) | **0.8ms** (illustrative) | 56,250x latency reduction via fast-fail |
| **P99 Latency under Outage** | 60,000ms+ (timeout) (illustrative) | **2.1ms** (illustrative) | System remains interactive |
| **Tomcat Busy Threads** | 200 / 200 (100% Saturation) | **4 / 200** (2% Utilization) | 98% thread pool headroom preserved |
| **Kubernetes Health Probe Latency** | Timed Out (>3,000ms) | **12ms** | Zero health check failures |
| **Pod Restarts / CrashLoops** | 20 pods in CrashLoopBackOff | **0 Restarts** | Complete infrastructure stability |
| **Downstream Hammering** | 20,000 calls/sec (Uncontrolled Retries) | **0 calls/sec** (During OPEN state) | Protects partner from collapse |
| **User Checkout Experience** | Hard 504 Gateway Timeout Error | **Queued for Async Settlement** | Zero lost orders |

---

## 11. Best Practices

- [x] **Always Separate Transient vs Non-Transient Exceptions:** Configure `ignoreExceptions` for validation errors (`PaymentValidationException`, 4xx Bad Request, 402 Card Declined). Only record network timeouts, connection resets, and 5xx errors in the Circuit Breaker.
- [x] **Always Configure Exponential Backoff with Jitter:** Never retry on a fixed interval (`100ms`). Use `enableExponentialBackoff: true` with multiplier (e.g. `2.0`) and randomized jitter to prevent the **Thundering Herd** problem against recovering downstreams.
- [x] **Isolate Sensitive Dependencies with Bulkheads:** Use `Bulkhead` (maxConcurrentCalls = 5–20) on downstream clients to ensure a single degraded partner cannot consume more than 10% of Tomcat's thread pool.
- [x] **Expose Actuator Endpoints for Alerting:** Enable `circuitbreakers` and `circuitbreakerevents` in Actuator so Prometheus can scrape `resilience4j_circuitbreaker_state` and trigger PagerDuty alerts immediately upon state transitions.
- [x] **Ensure Fallback Methods Have Matching Signatures:** The fallback method must have identical parameter types in identical order, plus the target `Throwable` (or specific subtype) as the trailing parameter.
- [x] **Never Wrap `@Transactional` with `@Retry` on the Same Method:** If `@Retry` is outer and `@Transactional` is inner, each retry initiates a fresh transaction upon failure. If `@Transactional` is outer, retrying an exception on an already-marked-rollback transaction will throw `UnexpectedRollbackException`.

---

## 12. Common Mistakes

### 1. The Broken Fallback Method Signature Trap
```java
// INCORRECT: Missing Throwable parameter in fallback signature!
// Resilience4j will throw NoSuchMethodException at runtime on the first failure!
@CircuitBreaker(name = "stripeGateway", fallbackMethod = "fallbackMethod")
public PaymentResponse charge(PaymentRequest request) { ... }

public PaymentResponse fallbackMethod(PaymentRequest request) { ... } // CRASH!

// CORRECT: Trailing Throwable parameter matches the signature
public PaymentResponse fallbackMethod(PaymentRequest request, Throwable ex) { ... }
```

### 2. Retrying Non-Idempotent HTTP Operations Without Idempotency Keys
Retrying a `POST /charges` endpoint that timed out at the socket layer can charge the customer's credit card twice if the gateway processed the payment before the connection dropped. Always attach a unique `Idempotency-Key` header generated on the client.

### 3. Placing `@Retry` Inside `@CircuitBreaker`
```
Misconfigured:
[CircuitBreaker] ──► [Retry: 3 attempts] ──► Downstream Call

Result:
1 failed request causes 3 immediate failures recorded in CircuitBreaker sliding window,
causing the circuit breaker to trip after only 2 user requests instead of 6!
```

### 4. Ignoring Slow Call Rate Thresholds
A downstream that responds in 20 seconds with HTTP 200 (Success) will NEVER trip a failure-rate-only circuit breaker! You must configure `slowCallRateThreshold: 50.0` and `slowCallDurationThreshold: 2000ms` so slow calls trip the circuit just like hard errors.

---

## 13. Interview Questions

### Junior Tier
**Q: What is the difference between a Retry and a Circuit Breaker?**  
*Answer:* A Retry attempts to re-execute a failed operation under the assumption that the fault is momentary and transient (e.g. a 50ms network hiccup). A Circuit Breaker detects systemic, prolonged downstream failures and actively *prevents* further calls from being made, fast-failing immediately to protect upstream resources and allow the downstream system time to recover.

---

### Mid Tier
**Q: How does a Count-Based sliding window differ from a Time-Based sliding window in Resilience4j?**  
*Answer:* A Count-Based sliding window records outcomes over the last $N$ calls (e.g. 100 calls) using a fixed-size circular array. It is ideal for steady-traffic services. A Time-Based sliding window records outcomes over the last $N$ seconds (e.g. 60 buckets of 1 second each), evaluating failure percentages over time regardless of call count fluctuations, making it ideal for variable or bursty traffic patterns.

---

### Senior Tier
**Q: What happens if a method annotated with both `@Transactional` and `@Retry` encounters an error?**  
*Answer:* If `@Transactional` wraps `@Retry` (outer transaction), the transaction is opened before the first attempt. If attempt 1 fails and marks the `TransactionStatus` as rollback-only, subsequent retries will execute within the same tainted transaction context. Upon method return, Hibernate/Spring will throw `UnexpectedRollbackException`. To fix this, `@Retry` must be the outermost decorator (or applied on a separate caller service bean) so each retry executes in a completely fresh `@Transactional(propagation = Propagation.REQUIRES_NEW)` boundary.

---

### Staff Tier
**Q: Why does Resilience4j favor SemaphoreBulkhead over ThreadPoolBulkhead for standard synchronous REST controllers? When would you use ThreadPoolBulkhead?**  
*Answer:* `SemaphoreBulkhead` uses atomic permit counters on the caller's existing thread with zero context-switching overhead and automatic `ThreadLocal` preservation (SecurityContext, MDC logging, Tracing span context). `ThreadPoolBulkhead` allocates an isolated `ThreadPoolExecutor` with a bounded queue; it is required only when executing asynchronous, non-blocking calls (`CompletableFuture`) or when true thread isolation is necessary to prevent runaway CPU loops from impacting sibling operations.

---

### Principal Tier
**Q: In a microservices fleet of 500 pods behind an API Gateway, how do you prevent a "Thundering Herd" when a Circuit Breaker transitions from OPEN to HALF_OPEN across all pods simultaneously?**  
*Answer:* If 500 pods transition to `HALF_OPEN` at the exact same second, each sending 5 probe requests, the downstream will instantly receive $500 \times 5 = 2,500$ concurrent requests, immediately knocking it back offline. Solutions include:
1. **Randomized Jitter in Open Duration:** Add Gaussian/uniform jitter to `waitDurationInOpenState` ($5000\text{ms} \pm 1500\text{ms}$) so pods transition to `HALF_OPEN` asynchronously over a dispersed window.
2. **Centralized Rate Limiting / Gateway-Level Circuit Breaking:** Enforce circuit breaking at the Envoy/Spring Cloud Gateway edge rather than pod-by-pod.
3. **Adaptive Backoff with Distributed Health Probing:** Have a single designated health-check probe daemon verify downstream recovery and broadcast state updates via Redis Pub/Sub.

---

## 14. Hands-on Exercise

### Task: Implement a Dynamic Circuit Breaker with Custom Fallback and Event Metrics
1. Create a `PaymentProcessingService` protected by Resilience4j.
2. Configure a sliding window of size 6, minimum calls 4, failure rate threshold 50%, and wait duration in open state 3000ms.
3. Write a unit test that:
   - Simulates 4 consecutive gateway 503 errors.
   - Asserts the circuit breaker state changes from `CLOSED` to `OPEN`.
   - Asserts that subsequent calls invoke the fallback and return `PaymentStatus.FALLBACK_QUEUED` with 0 gateway invocations.
   - Waits 3500ms, sends 3 successful calls, and verifies the state transitions through `HALF_OPEN` back to `CLOSED`.

### Solution
See complete runnable code in [ResilientPaymentServiceUnitTest.java](file:///D:/Projects/Spring%20Boot/spring-boot-production-guide/code/chapter-340/src/test/java/com/finflow/chapter340/unit/ResilientPaymentServiceUnitTest.java) and [PaymentCircuitBreakerIntegrationTest.java](file:///D:/Projects/Spring%20Boot/spring-boot-production-guide/code/chapter-340/src/test/java/com/finflow/chapter340/integration/PaymentCircuitBreakerIntegrationTest.java).

---

## 15. Advanced Challenge: Distributed Multi-Tenant Rate Limiting & Dynamic Circuit Configuration

### The Challenge
In a multi-tenant payment platform:
1. **Tiered Tenant Rate Limiting:** Premium merchants have a limit of 1,000 req/sec; Standard merchants have 100 req/sec; Basic merchants have 10 req/sec. Local in-memory `RateLimiter` instances fail across 20 pods because a tenant could send 20x their allowance across the cluster.
2. **Dynamic Runtime Configuration:** SREs must be able to adjust `failureRateThreshold` and `waitDurationInOpenState` at runtime via Spring Cloud Config or Actuator POST endpoints without redeploying the application.

### Enterprise Solution Architecture
```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                    Enterprise Multi-Tenant Distributed Resilience Architecture                   │
│                                                                                                 │
│  Inbound Request (Merchant: MERCH-994, Tier: PREMIUM)                                          │
│           │                                                                                     │
│           ▼                                                                                     │
│  [RateLimiterRegistry] ──► Dynamic lookup: RateLimiterRegistry.rateLimiter("tier_premium")      │
│           │                                                                                     │
│           ▼                                                                                     │
│  [Redis Token Bucket]  ──► EVALSHA redis_token_bucket.lua (Atomic CAS across all 20 pods)       │
│           │                                                                                     │
│           ▼                                                                                     │
│  [Dynamic Config Bus]  ──► Spring Cloud Bus / Actuator Endpoint updates CircuitBreakerConfig    │
│                            via CircuitBreaker.changeStateToOpen() or runtime reconfiguration.   │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 16. Production Checklist

Before approving PRs introducing downstream RPC calls or Resilience4j decorators:

- [ ] **Circuit Breaker Configured:** All external REST/gRPC/SOAP calls are guarded by a named `@CircuitBreaker`.
- [ ] **Timeouts Bound:** `RestTemplate`, `WebClient`, or `RestClient` has explicit connect timeouts ($\le 2\text{s}$) and read timeouts ($\le 5\text{s}$).
- [ ] **Exception Classification:** `ignoreExceptions` explicitly ignores business 4xx errors (`ValidationException`, `NotFoundException`); only 5xx and `IOException` are recorded.
- [ ] **Exponential Backoff & Jitter:** `@Retry` uses exponential backoff with jitter; fixed delays are prohibited.
- [ ] **Fallback Signatures Verified:** All fallback methods match target method parameter types exactly with a trailing `Throwable` parameter.
- [ ] **No Transaction Collision:** `@Retry` is never placed inside `@Transactional` on the same method.
- [ ] **Actuator Metrics Monitored:** `resilience4j.circuitbreaker.state` and `resilience4j.circuitbreaker.calls` are scraped by Prometheus with Grafana alerts configured for `state == OPEN`.
- [ ] **Bulkhead Sized Properly:** Bulkhead `maxConcurrentCalls` does not exceed 25% of the total application container thread pool.
