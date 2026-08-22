# Master Production Debugging Decision Tree

## Algorithmic Triage Framework for High-Severity Spring Boot Incidents

---

### 1. Master Diagnostic Entry Point

```text
                                  [ALERT / OUTAGE DETECTED]
                                              │
                    ┌─────────────────────────┴─────────────────────────┐
                    ▼                                                   ▼
         [Latency / Slow API]                                [Error Rate Spike (5xx / 4xx)]
                    │                                                   │
         (See Section 2: Latency)                            (See Section 3: Errors)
                    │                                                   │
                    └─────────────────────────┬─────────────────────────┘
                                              ▼
                    ┌─────────────────────────┴─────────────────────────┐
                    ▼                                                   ▼
       [Resource Exhaustion (CPU / OOM)]                  [Async / Streaming / Deployment]
         (See Section 4: Resources)                          (See Section 5: Streaming/Deploy)
```

---

### 2. High Latency & API Slowness Decision Tree

```text
[API P99 Latency > 1000ms]
  │
  ├──► Check Database Metrics: Is HikariCP connection pool exhausted?
  │     ├── YES ──► Check PostgreSQL `pg_stat_activity`:
  │     │            ├── Blocked by `AccessExclusiveLock`? ──► DDL lock queue! ──► Run `SELECT pg_terminate_backend(pid)` (Module 25)
  │     │            └── Un-indexed Slow Query / Table Scan? ──► Kill long query; Add index; Check OSIV (Module 08/09/10)
  │     └── NO ──► Check Downstream REST / External Dependencies:
  │                  ├── Downstream API slow (>3s)? ──► Bulkhead / Timeout exhaustion! ──► Enable CircuitBreaker fallback (Module 12)
  │                  └── Downstream API normal? ──► Proceed to Thread Inspection.
  │
  └──► Check JVM Thread Dump (`jcmd <PID> Thread.print`):
        ├── Virtual Threads blocked on `synchronized`? ──► Carrier thread pinning! ──► Replace with `ReentrantLock` (Module 10)
        ├── Threads blocked on `LockSupport.park` waiting for Redis? ──► Redis cache stampede! ──► Apply XFetch / Mutex (Module 19)
        └── High CPU on threads in `Pattern.matcher`? ──► Catastrophic regex backtracking! ──► Block input at WAF (Module 02)
```

---

### 3. Error Rate Spike (HTTP 5xx / 4xx) Decision Tree

```text
[HTTP 5xx / 4xx Spike]
  │
  ├──► HTTP 500 / 503 Internal Server Errors:
  │     ├── `PSQLException: column "xyz" does not exist`? ──► Breaking schema migration during rolling deploy! (Module 25/26)
  │     │    └── Fix: Recreate view/column alias immediately; Follow Expand & Contract.
  │     ├── `SSLHandshakeException: PKIX path building failed`? ──► Expired/Missing CA Cert! (Module 07)
  │     │    └── Fix: Import CA cert into JVM truststore; Reload pods.
  │     ├── `Connection is not available, request timed out`? ──► Connection pool exhaustion! (Module 09)
  │     │    └── Fix: Increase pool size / terminate blocking DB transactions.
  │     └── `Token used before issued_at` / `Token expired`? ──► Distributed Clock Skew (NTP drift)! (Module 24)
  │          └── Fix: Resync `chronyd`; Add 10s leeway window in JWT validator.
  │
  └──► HTTP 502 / 504 Gateway Errors:
        ├── 502 Bad Gateway ──► Upstream pod crashed / terminated abruptly during rolling update! (Module 26)
        │    └── Fix: Enable graceful shutdown (`server.shutdown=graceful`) and preStop sleep hook.
        └── 504 Gateway Timeout ──► Nginx proxy_read_timeout exceeded before Spring Boot responded! (Module 18)
             └── Fix: Trace upstream dependency bottleneck using OpenTelemetry spans.
```

---

### 4. Resource Exhaustion (CPU 100%, OOMKilled, Memory Growth)

```text
[Resource Exhaustion]
  │
  ├──► Kubernetes Pod OOMKilled (`Exit Code 137`):
  │     ├── JVM Heap Metric < 50%? ──► Native Memory Leak / Glibc fragmentation! (Module 01/14)
  │     │    └── Fix: Set `MALLOC_ARENA_MAX=2` or switch to `jemalloc`.
  │     ├── Heap Metric 100% + `OutOfMemoryError: Java heap space`? ──► Leaked collections / multipart `getBytes()`! (Module 14/23)
  │     │    └── Fix: Capture Heap Dump (`jcmd <PID> GC.heap_dump`); Analyze Dominator Tree in Eclipse MAT.
  │     └── `OutOfMemoryError: Metaspace`? ──► Dynamic proxy / classloader leak! (Module 14)
  │          └── Fix: Increase `-XX:MaxMetaspaceSize` and eliminate un-cached reflection generators.
  │
  └──► CPU 100% Utilization:
        ├── Continuous Full GC cycles (`GC thrashing`)? ──► Old Gen memory saturation! (Module 14)
        │    └── Fix: Scale pod heap or eliminate long-lived caching leaks.
        └── Application threads running in tight loops? ──► Regex backtracking / Hash collisions! (Module 02/13)
             └── Fix: Sample thread CPU via `top -H -p <PID>` and take thread dump.
```

---

### 5. Async, Kafka Streaming & Deployment Failures

```text
[Streaming & Deployment Failures]
  │
  ├──► Kafka Consumer Lag Explosion:
  │     ├── Continuous Group Rebalancing? ──► Batch processing exceeded `max.poll.interval.ms`! (Module 20)
  │     │    └── Fix: Reduce `max.poll.records`; Switch to `CooperativeStickyAssignor`.
  │     └── Consumer crashed on single record? ──► Poison Pill Deserialization error! (Module 20)
  │          └── Fix: Configure `ErrorHandlingDeserializer` + `DeadLetterPublishingRecoverer`.
  │
  ├──► Kubernetes Pod Rollout Stall:
  │     ├── `Unable to obtain table lock for flyway_schema_history`? ──► Orphaned Flyway lock! (Module 25)
  │     │    └── Fix: Clear failed lock; Move Flyway to single-replica Pre-Sync Job.
  │     └── Readiness Probe Failing on All Pods? ──► DB health check timeout flapping! (Module 14/17)
  │          └── Fix: Separate `/actuator/health/readiness` from external DB checks.
  │
  └──► Distributed Transaction Inconsistency:
        ├── Money debited but order unfulfilled? ──► Distributed partial failure without Saga rollback! (Module 27)
        │    └── Fix: Implement `PaymentSagaOrchestrator` with automated reverse compensation.
        └── DB committed but Kafka event missing? ──► Dual-write network failure! (Module 27)
             └── Fix: Implement Transactional Outbox Pattern with CDC relay.
```

---

*(End of Master Debugging Decision Tree)*
