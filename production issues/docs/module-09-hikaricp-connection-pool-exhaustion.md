# Module 09: Database & HikariCP Connection Pool Exhaustion

## Issue 9.1: Connection Pool Starvation, Leaks, and Sizing Hazards

---

### 1. Scenario

During the Black Friday flash sale on the **FinFlow Core Settlement Engine**:
1. All incoming payment settlement requests fail with `500 Internal Server Error` or hang indefinitely.
2. Application logs are flooded with:
   ```text
   org.springframework.dao.CannotCreateTransactionException: Could not open JPA EntityManager for transaction
   Caused by: java.sql.SQLTransientConnectionException: FinFlowHikariPool - Connection is not available, request timed out after 30000ms.
   ```
3. Tomcat's HTTP worker thread pool (200 threads) completely saturates because every thread is blocked waiting to acquire a physical database connection.
4. Telemetry reveals that a developer wrapped the entire settlement flow in `@Transactional`, causing threads to hold database connections open for 4 to 8 seconds while waiting for an external banking REST gateway response!

---

### 2. Symptoms

```text
1. Connection Acquisition Timeout: java.sql.SQLTransientConnectionException: HikariPool-1 - Connection is not available, request timed out after 30000ms.
2. Connection Leak Warnings: WARN com.zaxxer.hikari.pool.ProxyLeakTask - Apparent connection leak detected, connection conn3...
3. Cascading Worker Starvation: Tomcat/Undertow worker threads blocked in WAITING state at com.zaxxer.hikari.pool.HikariPool.getConnection.
4. Prometheus Telemetry Saturation:
   - hikaricp_connections_active == hikaricp_connections_max (100% pool utilization)
   - hikaricp_connections_pending spiking to hundreds of threads
   - hikaricp_connections_acquire_seconds surging above connectionTimeout
5. Stale Connection Failures: Broken pipe / Connection reset by peer after idle periods (e.g. AWS NAT Gateway 350s idle drop).
```

---

### 3. Possible Root Causes

1. **Holding Database Connections During External I/O (Anti-Pattern):** Placing `@Transactional` on methods that execute remote HTTP/REST calls, gRPC calls, or slow file I/O locks a pooled JDBC connection for the entire duration of the remote network latency.
2. **Unclosed Raw JDBC Connections:** Borrowing a connection via `dataSource.getConnection()` without closing it in a `try-with-resources` block permanently leaks the connection from the pool.
3. **Improper Pool Sizing:** Oversizing the pool (`maximum-pool-size: 500`) causes CPU thrashing and DB lock contention, while undersizing without rate limiting causes rapid pool starvation.
4. **Stale Connection Drops by Cloud Firewalls:** AWS NAT Gateways, Azure Load Balancers, and stateful firewalls silently terminate idle TCP sessions after 350 seconds. If `max-lifetime` is higher than this firewall timeout, HikariCP loans dead connections to application threads.

---

### 4. Architecture Context: HikariCP FastPath & Connection Lifecycle

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        HIKARICP CONNECTION ACQUISITION LIFECYCLE                       │
│                                                                                        │
│  [Application Thread]                                                                  │
│           │                                                                            │
│           ▼                                                                            │
│  1. Check Thread-Local Bag (FastPath: zero-lock thread handoff)                       │
│           │                                                                            │
│     Found?├──► [YES] ──► Validate & Borrow Connection Immediately (< 100ns)            │
│           │                                                                            │
│          [NO]                                                                          │
│           ▼                                                                            │
│  2. Scan Shared ConcurrentBag (Lock-free lockless queue)                               │
│           │                                                                            │
│     Found?├──► [YES] ──► Mark Connection in-use & Borrow                               │
│           │                                                                            │
│          [NO]                                                                          │
│           ▼                                                                            │
│  3. Wait on SynchronousQueue / Semaphore until connectionTimeout (e.g. 2000ms)         │
│           │                                                                            │
│     Released before timeout?                                                           │
│           ├──► [YES] ──► Borrow connection                                             │
│           └──► [NO]  ──► 💥 Throw SQLTransientConnectionException                      │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### Step 1: Constrain Connection Pool
```yaml
spring:
  datasource:
    hikari:
      pool-name: FinFlowHikariPool
      maximum-pool-size: 3 # Only 3 connections allowed
      connection-timeout: 2000 # 2-second timeout
      leak-detection-threshold: 2000 # Log warning if held > 2s
```

#### Step 2: Simulate Long Transaction Holding DB Connection
```java
// ❌ ANTI-PATTERN: Holding DB connection during external HTTP call
@Transactional
public void processPayment(BigDecimal amount) {
    settlementRepository.save(new SettlementEntity(...)); // Borrows connection from pool
    
    // Simulate slow external third-party API call (e.g. Stripe/PayPal)
    callExternalBankGateway(); // Takes 5,000ms! Connection is locked for 5s!
    
    settlementRepository.updateStatus(...);
}
```

#### Step 3: Run 4 Concurrent Threads
Under 4 concurrent requests with `maximum-pool-size: 3`, the 4th thread will block for 2,000ms and fail with `SQLTransientConnectionException`!

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Inspect Actuator Prometheus Metrics
Query the `/actuator/prometheus` endpoint:
```text
# HELP hikaricp_connections_active Active connections in the pool
# TYPE hikaricp_connections_active gauge
hikaricp_connections_active{pool="FinFlowHikariPool"} 3.0

# HELP hikaricp_connections_pending Threads waiting for a connection
# TYPE hikaricp_connections_pending gauge
hikaricp_connections_pending{pool="FinFlowHikariPool"} 14.0

# HELP hikaricp_connections_idle Idle connections in the pool
# TYPE hikaricp_connections_idle gauge
hikaricp_connections_idle{pool="FinFlowHikariPool"} 0.0
```

#### Method 2: Capture JVM Thread Dump via `jstack`
```bash
jstack <PID> | grep -A 10 "HikariPool"
```
**Diagnostic Output:**
```text
"http-nio-8080-exec-4" #42 daemon prio=5 tid=0x00007f... nid=0x1a2b waiting on condition
   java.lang.Thread.State: TIMED_WAITING (parking)
	at jdk.internal.misc.Unsafe.park(java.base@21.0.3/Native Method)
	- parking to wait for  <0x0000000712345678> (a java.util.concurrent.SynchronousQueue$TransferStack)
	at java.util.concurrent.locks.LockSupport.parkNanos(java.base@21.0.3/LockSupport.java:269)
	at com.zaxxer.hikari.util.ConcurrentBag.borrow(ConcurrentBag.java:152)
	at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:181)
```

#### Method 3: HikariCP Leak Detection Stack Trace
When `leak-detection-threshold` is set, HikariCP logs the exact stack trace where the leaked connection was borrowed:
```text
WARN 27928 --- [FinFlowHikariPool housekeeper] com.zaxxer.hikari.pool.ProxyLeakTask : 
Apparent connection leak detected, connection conn3 (created 2001ms ago) was borrowed from pool:
	at com.finflow.troubleshooting.module09.service.LeakSimulationService.simulateUnclosedRawJdbcConnection(LeakSimulationService.java:46)
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Verify Connection Pool Saturation in Prometheus/Grafana.
        Confirm if hikaricp_connections_active == hikaricp_connections_max and
        hikaricp_connections_pending > 0.

Step 2: Enable HikariCP Leak Detection in Staging / Production.
        Set spring.datasource.hikari.leak-detection-threshold: 2000 (2 to 5 seconds).
        Review application logs for ProxyLeakTask stack traces.

Step 3: Audit Transaction Boundaries for Remote Network Calls.
        Grep for RestTemplate, WebClient, FeignClient, or HttpClient inside @Transactional classes.
        Remove @Transactional from the orchestrator and scope it strictly to database CRUD methods.

Step 4: Audit Raw JDBC Usage.
        Ensure all Connection, Statement, and ResultSet instances use try-with-resources.

Step 5: Apply Mathematical Pool Sizing Formula.
        Calculate pool size based on hardware cores and disk spindles, not arbitrary large numbers.
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. Why `@Transactional` Holds a DB Connection During External I/O
- Spring's `JpaTransactionManager` / `DataSourceTransactionManager` acquires a physical JDBC `Connection` from HikariCP at the **start** of a `@Transactional` method.
- The connection is bound to the current thread via `TransactionSynchronizationManager.bindResource(dataSource, connectionHolder)`.
- It remains bound to the thread and **in-use** in HikariCP until the method exits and Spring executes `commit()` or `rollback()`.
- If an external REST API takes 5 seconds, that database connection is completely useless to the entire application for those 5 seconds.

#### 2. The PostgreSQL / Oracle Pool Sizing Formula
Many developers believe: *"If we have 200 web threads, we need 200 database connections."* **This is completely false.**
Adding more connections beyond database CPU core capacity degrades throughput due to disk seek thrashing, context switching, and OS scheduler overhead.

The golden HikariCP formula (developed by PostgreSQL performance engineers):
$$\text{Pool Size} = (\text{Core Count} \times 2) + \text{Effective Spindle Count}$$

- For an 8-core database server with SSDs ($\text{spindle} = 1$):
  $$\text{Pool Size} = (8 \times 2) + 1 = 17 \text{ connections}$$
- A pool of **17 to 25 connections** can easily handle thousands of requests per second if each transaction completes in under 5 milliseconds!

#### 3. The 350-Second Cloud Firewall Silent Drop Trap
- Cloud middleboxes (e.g. AWS NAT Gateway, AWS ALB) drop idle TCP sessions after **350 seconds** without sending a `TCP RST` packet.
- If HikariCP's `max-lifetime` is set to the default (30 minutes = 1,800,000ms), HikariCP will attempt to borrow a dead TCP socket from the pool.
- **Rule:** Always set `max-lifetime` to at least **30 seconds less** than your network firewall timeout (e.g., `max-lifetime: 300000` = 5 minutes on AWS).

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Refactoring Long Transactions (Separating DB from I/O)
```java
@Service
public class PaymentSettlementOrchestrator {

    private final SettlementDatabaseService dbService;
    private final ExternalBankingClient bankingClient;

    // Non-transactional orchestrator: does NOT hold a DB connection during remote call!
    public String processSettlement(PaymentRequest request) {
        // Step 1: Short DB Transaction to persist PENDING state
        String txnId = dbService.createPendingSettlement(request);

        // Step 2: Slow External Network Call (No DB connection held!)
        BankResponse response = bankingClient.executeExternalCall(request);

        // Step 3: Short DB Transaction to update SETTLED state
        dbService.updateSettlementStatus(txnId, response.getStatus());

        return txnId;
    }
}

@Service
public class SettlementDatabaseService {

    @Transactional
    public String createPendingSettlement(PaymentRequest request) {
        // Holds DB connection for ~2 milliseconds only!
        return repository.save(new SettlementEntity(...)).getTransactionId();
    }

    @Transactional
    public void updateSettlementStatus(String txnId, String status) {
        // Holds DB connection for ~2 milliseconds only!
        SettlementEntity entity = repository.findByTransactionId(txnId).orElseThrow();
        entity.setStatus(status);
    }
}
```

#### ✅ Fix 2: Production-Hardened `application.yml`
```yaml
spring:
  datasource:
    hikari:
      pool-name: FinFlowHikariPool
      maximum-pool-size: 20 # Sized according to (cores * 2) + spindles
      minimum-idle: 10
      connection-timeout: 3000 # Fail-fast after 3 seconds if pool exhausted
      idle-timeout: 120000 # 2 minutes
      max-lifetime: 300000 # 5 minutes (lower than AWS NAT Gateway 350s drop)
      leak-detection-threshold: 3000 # Logs stack trace if connection borrowed > 3s
      connection-test-query: SELECT 1 # Or rely on JDBC4 isValid()
      validation-timeout: 250
```

#### ✅ Fix 3: Programmatic Transaction Boundary with `TransactionTemplate`
```java
@Service
public class ProgrammaticSettlementService {

    private final TransactionTemplate transactionTemplate;
    private final ExternalBankingClient bankingClient;

    public void settle(PaymentRequest req) {
        // DB transaction 1
        String txnId = transactionTemplate.execute(status -> repository.save(...).getId());

        // External I/O without connection
        BankResult result = bankingClient.call(req);

        // DB transaction 2
        transactionTemplate.execute(status -> {
            repository.update(...);
            return null;
        });
    }
}
```

---

### 10. Verification

1. **Metrics Test:** Run `HikariPoolMetricsTest.java` to verify pool name, maximum size, and total connection counters.
2. **Concurrent Starvation Test:** Run `ConnectionPoolStarvationTest.java` to confirm concurrent threads borrow and return connections within pool thresholds without timeouts.
3. **Integration Test:** Run `Module09IntegrationTest.java` to verify actuator metrics endpoint and settlement execution.

---

### 11. Prevention & Production Readiness

1. **Grafana Prometheus Alerting Rules:**
   ```yaml
   - alert: HikariCPConnectionPoolExhausted
     expr: (hikaricp_connections_pending{pool="FinFlowHikariPool"} > 5)
     for: 1m
     labels:
       severity: critical
     annotations:
       summary: "HikariCP pool {{ $labels.pool }} has pending threads waiting for connections"
   ```
2. **Never Execute Network Calls in `@Transactional` Methods:**
   Add ArchUnit architectural tests in CI/CD to prevent `HttpClient`, `RestTemplate`, or `FeignClient` calls inside `@Transactional` methods.
3. **Set `leak-detection-threshold` in Production:**
   Keep `leak-detection-threshold: 5000` permanently enabled in production. The overhead is negligible (a single scheduled task per connection loan).

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: How does HikariCP achieve its extreme performance compared to older pools like C3P0 or DBCP?**
2. **Q: What is the recommended formula for sizing a database connection pool in a microservice architecture?**
3. **Q: What happens if `maximum-pool-size` is set to 500 on an application connecting to an 8-core database server?**
4. **Q: How does HikariCP's `leak-detection-threshold` work internally without impacting query performance?**
5. **Q: Why does wrapping a third-party REST call in `@Transactional` exhaust database connection pools?**

#### Production Incident Questions
1. **Incident:** During peak hours, an API experiences `SQLTransientConnectionException` timeouts, but database CPU utilization is only 12%. What is the root cause and how do you investigate?
2. **Incident:** After migrating from on-premise to AWS, an application throws `Connection reset` every morning after 6 minutes of inactivity. What HikariCP parameter must be tuned?
3. **Incident:** You observe `hikaricp_connections_pending` spiking to 50 during a third-party service outage. How does an external outage affect database connection pools?
4. **Incident:** A developer used raw `DataSource.getConnection()` inside a custom reporting service. Over 3 days, the connection pool slowly degraded until no connections were left. How do you find the leak?
5. **Incident:** What is the difference between `connection-timeout` and `idle-timeout` in HikariCP?

#### Trick Questions
1. **Trick:** If `minimum-idle` is not configured in HikariCP, what does it default to?
2. **Trick:** Does increasing `maximum-pool-size` in your Spring Boot configuration automatically allow your database to handle more connections?
3. **Trick:** If a method has `@Transactional(readOnly = true)`, does HikariCP still allocate a physical database connection from the pool?

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
