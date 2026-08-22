# Module 28: Production Incident Response (20 Comprehensive Scenarios)

## Master Incident Command System (ICS), Real-Time Triage & 20 High-Severity Enterprise Production Incident Runbooks

---

### 1. Scenario: The Master Production Incident Command Center

In an enterprise fintech ecosystem processing **$2.5B in daily transactions across 80+ microservices**:
1. At **14:02 UTC**, a cascading failure strikes:
   - A rolling deployment triggers an in-memory session loss storm.
   - An `ALTER TABLE` statement on PostgreSQL locks the `accounts` table without a lock timeout.
   - Upstream payment pods exhaust HikariCP connections, causing health probes to flap.
   - Kubernetes evicts failing pods, triggering a Kafka consumer rebalance death spiral on payment settlement topics.
2. Within 90 seconds, **customer transaction failure rate surges to 88%**, generating $400,000 per minute in revenue loss.
3. This Master Capstone Module provides the **complete operational framework, incident triage engine, and detailed diagnostic runbooks for all 20 mission-critical production incident archetypes**.

---

### 2. Incident Command System (ICS) & Severity Matrix

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                       ENTERPRISE SEVERITY CLASSIFICATION MATRIX                                │
├──────────────┬───────────────────────────────┬────────────┬────────────┬────────────────────────┤
│ Severity     │ Business & Customer Impact    │ MTTD Target│ MTTR Target│ Escalation Protocol    │
├──────────────┼───────────────────────────────┼────────────┼────────────┼────────────────────────┤
│ SEV-1        │ Critical Outage / Revenue     │ < 2 min    │ < 15 min   │ Page Incident Commander│
│ (Critical)   │ Loss / Data Loss / Core Down  │            │            │ Tech Lead, Open WarRoom│
├──────────────┼───────────────────────────────┼────────────┼────────────┼────────────────────────┤
│ SEV-2        │ Major Degradation / Redundancy│ < 5 min    │ < 45 min   │ Page On-Call SRE &     │
│ (Major)      │ Impaired / High Latency       │            │            │ Service Domain Lead    │
├──────────────┼───────────────────────────────┼────────────┼────────────┼────────────────────────┤
│ SEV-3        │ Minor Bug / Internal Tooling  │ < 30 min   │ < 4 hours  │ Standard Jira Ticket / │
│ (Minor)      │ Impact / Zero Revenue Loss    │            │            │ Next Business Day      │
└──────────────┴───────────────────────────────┴────────────┴────────────┴────────────────────────┘
```

#### Incident Command Roles
1. **Incident Commander (IC):** Drives the response, delegates tasks, maintains focus on *mitigation over root-cause finding*, and makes the final go/no-go rollback decisions.
2. **Technical Lead (TL):** Coordinates diagnostic investigations, tests hypotheses, and executes safe mitigation runbooks.
3. **Communications Lead (CL):** Updates internal stakeholders, executive leadership, and public status pages every 15 minutes.
4. **Scribe:** Chronologically logs all findings, hypothesis tests, and actions executed in the war room.

---

### 3. Architecture Context: The 14-Step Universal Debugging Protocol

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                 UNIVERSAL 14-STEP PRODUCTION INCIDENT DEBUGGING PROTOCOL                        │
│                                                                                                 │
│  [PHASE 1: DETECTION & TRIAGE]                                                                  │
│  1. Ingest PagerDuty/Prometheus Alert ──► 2. Classify Severity (SEV-1/2/3) ──► 3. Open War Room │
│                                                                                                 │
│  [PHASE 2: TRIAGE & ISOLATION]                                                                  │
│  4. Inspect Golden Signals (Latency, Traffic, Errors, Saturation)                                │
│  5. Identify Blast Radius & Affected Deployments / Canary Revisions                             │
│  6. Preserve Evidence (Thread Dump, Heap Dump, Linux dmesg, pg_stat_activity)                   │
│                                                                                                 │
│  [PHASE 3: RAPID MITIGATION] (Mitigate First, Investigate Later!)                               │
│  7. Execute Runbook: (Rollback Deploy | Scale Pods | Terminate Blocking Lock | Enable Fallback) │
│  8. Validate Service Recovery via Synthetics & Real-User Telemetry                              │
│                                                                                                 │
│  [PHASE 4: ROOT CAUSE INVESTIGATION]                                                            │
│  9. Analyze Heap / Thread / NMT Dumps ──► 10. Recreate in Local Lab ──► 11. Implement Fix       │
│                                                                                                 │
│  [PHASE 5: POST-MORTEM & PREVENTION]                                                            │
│  12. Conduct Blameless Post-Mortem ──► 13. Map 5 Whys ──► 14. Deploy Chaos & ArchUnit Guards    │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 4. Catalog of 20 Enterprise Incident Playbooks

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                      CATALOG OF 20 ENTERPRISE INCIDENT PLAYBOOKS                                │
├────┬─────────────────────────────────────────────────┬──────────┬───────────────────────────────┤
│ ID │ Incident Scenario Title                         │ Severity │ Failure Domain / Module Ref   │
├────┼─────────────────────────────────────────────────┼──────────┼───────────────────────────────┤
│ 01 │ JVM Native Memory Leak & Glibc Fragmentation    │ SEV-1    │ JVM Memory (Module 01/14)     │
│ 02 │ PostgreSQL AccessExclusiveLock Pool Exhaustion  │ SEV-1    │ Database Locks (Module 25)    │
│ 03 │ Kafka Consumer Lag Rebalance Death Spiral       │ SEV-1    │ Kafka Streaming (Module 20)   │
│ 04 │ Redis Cache Stampede & Distributed Mutex Herd   │ SEV-1    │ Redis Caching (Module 19)     │
│ 05 │ Virtual Thread Carrier Pinning on Synchronized  │ SEV-2    │ Concurrency (Module 10)       │
│ 06 │ SSL/TLS Certificate Expiry & Truststore Failure │ SEV-1    │ Networking/TLS (Module 07)    │
│ 07 │ Distributed Clock Skew (NTP) & JWT Invalidation │ SEV-1    │ Core Time (Module 24)         │
│ 08 │ Rolling Deploy Session Invalidation Logout Storm│ SEV-1    │ Deployment (Module 26)        │
│ 09 │ Flyway Migration Lock Orphan & Rollout Stall    │ SEV-2    │ Database DevOps (Module 25)   │
│ 10 │ Zero-Downtime Column Rename Breaking Live Pods  │ SEV-1    │ Database DevOps (Module 25)   │
│ 11 │ Distributed Partial Failure & Missing Saga Roll │ SEV-1    │ Distributed Sagas (Module 27) │
│ 12 │ Dual-Write Loss Between PostgreSQL and Kafka    │ SEV-1    │ Distributed Systems(Module 27)│
│ 13 │ DNS Resolution TTL Cache Caching Stale IP       │ SEV-1    │ Networking (Module 06)        │
│ 14 │ Ephemeral Disk Exhaustion via Upload Temp Files │ SEV-2    │ Web & Storage (Module 23)     │
│ 15 │ ShedLock Distributed Job Overlap on Partition   │ SEV-2    │ Scheduling (Module 22)        │
│ 16 │ RabbitMQ Memory Alarm & Unacked Message Flood   │ SEV-1    │ Messaging (Module 16)         │
│ 17 │ Microservice Cascading Thread Pool Exhaustion   │ SEV-1    │ Resilience (Module 12)        │
│ 18 │ CPU 100% via Regex Catastrophic Backtracking    │ SEV-1    │ Core Runtime (Module 02)      │
│ 19 │ Kubernetes Readiness Probe Flapping Blackout    │ SEV-1    │ Kubernetes (Module 14/17)     │
│ 20 │ Out-of-Order Kafka Consumption Ledger Corruption│ SEV-1    │ Kafka Streaming (Module 20)   │
└────┴─────────────────────────────────────────────────┴──────────┴───────────────────────────────┘
```

---

### 5. Detailed Technical Runbooks for All 20 Incident Scenarios

---

#### 🔴 Playbook 01: JVM Native Memory Leak & Glibc Arena Fragmentation
- **Severity:** `SEV-1` | **Domain:** `JVM Memory / C-Heap`
- **Symptoms:** Kubernetes pod OOMKilled (`Exit Code 137`); JVM heap metrics report $<50\%$ memory utilization, but container Resident Set Size (RSS) steadily breaches cgroup limits.
- **Diagnostic Command:**
  ```bash
  # Check native memory tracking breakdown
  jcmd <PID> VM.native_memory detail.diff
  ```
- **Root Cause:** Glibc's default memory allocator creates up to $8 \times \text{cores}$ memory arenas. Rapid thread creation and destruction results in severe C-heap fragmentation.
- **Immediate Mitigation:**
  > [!WARNING]
  > ⚠️ Do not run blindly in production: Injecting environment variables triggers a rolling restart of all pods.
  ```bash
  kubectl set env deployment/finflow-payments MALLOC_ARENA_MAX=2
  ```
- **Permanent Remediation:** Switch container base image to `jemalloc` (`LD_PRELOAD=/usr/lib/libjemalloc.so`) and configure fixed thread pools.

---

#### 🔴 Playbook 02: PostgreSQL AccessExclusiveLock Cascading Pool Exhaustion
- **Severity:** `SEV-1` | **Domain:** `Database Internals / Connection Pools`
- **Symptoms:** HikariCP connection pool exhausted in $<3\text{s}$ (`Connection is not available, request timed out after 30000ms`); 100% of API endpoints timeout with 500 errors.
- **Diagnostic Command:**
  ```sql
  SELECT blocked_locks.pid AS blocked_pid, blocking_locks.pid AS blocking_pid, 
         blocked_activity.query AS blocked_query, blocking_activity.query AS blocking_query
  FROM pg_catalog.pg_locks blocked_locks
  JOIN pg_catalog.pg_locks blocking_locks ON blocking_locks.relation = blocked_locks.relation
  JOIN pg_catalog.pg_stat_activity blocked_activity ON blocked_activity.pid = blocked_locks.pid
  JOIN pg_catalog.pg_stat_activity blocking_activity ON blocking_activity.pid = blocking_locks.pid
  WHERE NOT blocked_locks.granted;
  ```
- **Root Cause:** DDL `ALTER TABLE` executed without `lock_timeout` queued behind an analytical query, acquiring `AccessExclusiveLock` and blocking all subsequent `SELECT` and `UPDATE` traffic.
- **Immediate Mitigation:**
  > [!WARNING]
  > ⚠️ Do not run blindly in production: Terminating a backend query aborts that transaction immediately.
  ```sql
  SELECT pg_terminate_backend(<BLOCKING_PID>);
  ```
- **Permanent Remediation:** Enforce `SET lock_timeout = '2s';` in all Flyway migrations and execute schema changes via the 4-phase Expand and Contract pattern.

---

#### 🔴 Playbook 03: Kafka Consumer Lag Rebalance Death Spiral
- **Severity:** `SEV-1` | **Domain:** `Messaging / Kafka`
- **Symptoms:** Consumer group continuously rebalances; consumer lag grows exponentially; messages are processed repeatedly.
- **Diagnostic Command:**
  ```bash
  kafka-consumer-groups.sh --bootstrap-server kafka:9092 --describe --group payments-group
  ```
- **Root Cause:** Downstream processing latency caused batch handling to exceed `max.poll.interval.ms` (300s). Broker marks consumer dead and triggers group rebalance, halting all processing.
- **Immediate Mitigation:**
  ```bash
  kubectl scale deployment/kafka-consumer-payments --replicas=12
  ```
- **Permanent Remediation:** Tune `max.poll.records=100`, increase `max.poll.interval.ms=600000`, and configure `CooperativeStickyAssignor`.

---

#### 🔴 Playbook 04: Redis Cache Stampede & Distributed Mutex Thundering Herd
- **Severity:** `SEV-1` | **Domain:** `Distributed Caching`
- **Symptoms:** Hot cache key expires; thousands of concurrent threads query PostgreSQL simultaneously; database CPU spikes to 100%.
- **Diagnostic Command:**
  ```bash
  redis-cli --latency-history
  ```
- **Root Cause:** Hard TTL expiration on high-traffic keys without probabilistic refresh or single-flight mutex locking.
- **Immediate Mitigation:** Pre-warm the expired key manually via `redis-cli SET <KEY> <VALUE> EX 3600`.
- **Permanent Remediation:** Implement XFetch Probabilistic Early Expiration algorithm and distributed mutex lock for cache misses.

---

#### 🟡 Playbook 05: Virtual Thread Carrier Pinning on Synchronized Blocks
- **Severity:** `SEV-2` | **Domain:** `JVM Concurrency`
- **Symptoms:** Virtual thread throughput collapses; carrier thread pool (`ForkJoinPool`) saturates during blocking database or network I/O.
- **Diagnostic Command:**
  ```bash
  # Enable JDK virtual thread pinning trace
  -Djdk.tracePinnedThreads=full
  ```
- **Root Cause:** Virtual threads executing blocking I/O inside `synchronized` blocks or native JNI calls pin the underlying OS carrier thread.
- **Immediate Mitigation:** Scale out application pod replicas to increase available carrier pool capacity.
- **Permanent Remediation:** Replace `synchronized` blocks with `java.util.concurrent.locks.ReentrantLock`.

---

#### 🔴 Playbook 06: SSL/TLS Certificate Expiry & Truststore Handshake Failures
- **Severity:** `SEV-1` | **Domain:** `Networking / Security`
- **Symptoms:** Outbound HTTPS requests to payment gateways fail with `SSLHandshakeException: PKIX path building failed: unable to find valid certification path`.
- **Diagnostic Command:**
  ```bash
  openssl s_client -connect api.stripe.com:443 -showcerts
  ```
- **Root Cause:** Upstream payment provider rotated root/intermediate CA certificate; container JVM truststore (`cacerts`) lacks the new public cert.
- **Immediate Mitigation:**
  > [!WARNING]
  > ⚠️ Do not run blindly in production: Overwriting truststores will fail if permissions are invalid.
  ```bash
  keytool -importcert -alias new-ca -keystore /etc/ssl/certs/java/cacerts -file ca.crt -storepass changeit -noprompt
  kubectl rollout restart deployment/payment-gateway
  ```
- **Permanent Remediation:** Deploy automated cert-manager with 30-day proactive rotation alerts and reloadable SSL bundles.

---

#### 🔴 Playbook 07: Distributed Clock Skew (NTP Drift) & JWT Premature Invalidation
- **Severity:** `SEV-1` | **Domain:** `Time & Security`
- **Symptoms:** 100% of JWT authorization tokens rejected with `Token used before issued_at` or `Token expired` despite being generated milliseconds earlier.
- **Diagnostic Command:**
  ```bash
  chronyc tracking
  ```
- **Root Cause:** Node 1 wall-clock drifted $>4\text{s}$ ahead of Node 2 due to NTP daemon desynchronization.
- **Immediate Mitigation:** Restart `chronyd` on out-of-sync nodes: `systemctl restart chronyd`.
- **Permanent Remediation:** Configure a $\pm 10\text{s}$ clock skew tolerance leeway window in JWT validator (`jwtVerifier.setAllowedClockSkewSeconds(10)`).

---

#### 🔴 Playbook 08: Rolling Deploy In-Memory Session Invalidation Storm
- **Severity:** `SEV-1` | **Domain:** `Release Engineering`
- **Symptoms:** 150,000 users logged out simultaneously during rolling deploy; 15x login surge crashes OAuth2 identity provider.
- **Diagnostic Command:**
  ```bash
  kubectl logs -n ingress-nginx -l app.kubernetes.io/name=ingress-nginx | grep -E "POST /oauth/token" | wc -l
  ```
- **Root Cause:** Application stored HTTP session state in local in-memory Tomcat heap; pod restarts destroyed active sessions.
- **Immediate Mitigation:** Throttle login gateway rate limits to prevent identity provider total collapse.
- **Permanent Remediation:** Migrate to stateless JWT authentication or Spring Session backed by distributed Redis.

---

#### 🟡 Playbook 09: Flyway Migration Lock Orphan & Rollout Stall
- **Severity:** `SEV-2` | **Domain:** `Database DevOps`
- **Symptoms:** Application pods stuck in `CrashLoopBackOff` with `Unable to obtain table lock for flyway_schema_history`.
- **Diagnostic Command:**
  ```sql
  SELECT * FROM flyway_schema_history WHERE success = false OR installed_rank = (SELECT max(installed_rank) FROM flyway_schema_history);
  ```
- **Root Cause:** A pod executing migration was OOMKilled mid-run, leaving the lock table in an orphaned locked state.
- **Immediate Mitigation:**
  > [!WARNING]
  > ⚠️ Do not run blindly in production: Ensure no other migration is actively writing data before running repair!
  ```sql
  -- Run flyway repair or unlock table
  DELETE FROM flyway_schema_history WHERE success = false;
  ```
- **Permanent Remediation:** Move Flyway execution out of application pods into a single-replica Kubernetes Pre-Sync Job.

---

#### 🔴 Playbook 10: Zero-Downtime Column Rename Breaking Rolling Deployments
- **Severity:** `SEV-1` | **Domain:** `Database DevOps`
- **Symptoms:** 50% of production traffic fails with `PSQLException: column "account_number" does not exist`.
- **Diagnostic Command:**
  ```bash
  kubectl logs -l app=finflow-banking | grep -i "column.*does not exist"
  ```
- **Root Cause:** Migration dropped or renamed column in a single step while older Version 1 pods were still serving traffic.
- **Immediate Mitigation:** Immediately roll back frontend deployment and recreate a database view or alias column in PostgreSQL.
- **Permanent Remediation:** Enforce 4-phase Expand and Contract pattern across independent software releases.

---

#### 🔴 Playbook 11: Distributed Partial Failure & Missing Saga Compensations
- **Severity:** `SEV-1` | **Domain:** `Distributed Systems`
- **Symptoms:** Customer account debited $5,000; FX conversion failed; order unfulfilled and money not refunded.
- **Diagnostic Command:**
  ```sql
  SELECT * FROM saga_instances WHERE status = 'COMPENSATING' OR status = 'FAILED';
  ```
- **Root Cause:** Synchronous REST chain without a Saga Coordinator crashed mid-flow without executing compensating rollbacks.
- **Immediate Mitigation:** Run manual ledger reconciliation script to credit customer wallet balances for orphaned orders.
- **Permanent Remediation:** Implement `PaymentSagaOrchestrator` with automated reverse compensation (`Refund Wallet -> Cancel Order`).

---

#### 🔴 Playbook 12: Dual-Write Loss Between PostgreSQL and Kafka Outbox
- **Severity:** `SEV-1` | **Domain:** `Data Consistency`
- **Symptoms:** Orders exist in PostgreSQL database but were never published to Kafka fulfillment topics.
- **Diagnostic Command:**
  ```sql
  SELECT count(*) FROM orders o WHERE NOT EXISTS (SELECT 1 FROM outbox_events e WHERE e.aggregate_id = o.id);
  ```
- **Root Cause:** Non-atomic dual-write (`save()` followed by `kafka.send()`); network partition caused Kafka send failure after DB commit.
- **Immediate Mitigation:** Execute backfill reconciliation script to scan un-published records and dispatch events to Kafka.
- **Permanent Remediation:** Implement Transactional Outbox Pattern to persist business entities and outbox events in a single ACID transaction.

---

#### 🔴 Playbook 13: DNS Resolution TTL Cache Caching Stale IP After Cloud Failover
- **Severity:** `SEV-1` | **Domain:** `Core Networking`
- **Symptoms:** Microservices fail to connect to database endpoint after AWS RDS multi-AZ failover (`Connection refused` / `UnknownHostException`).
- **Diagnostic Command:**
  ```bash
  nslookup <DATABASE_HOST>
  ```
- **Root Cause:** JVM default `networkaddress.cache.ttl` is `-1` (cached forever), causing the JVM to query the stale primary IP.
- **Immediate Mitigation:** Perform a rolling restart of all application pods to clear in-memory DNS cache.
- **Permanent Remediation:** Set `networkaddress.cache.ttl=10` in `$JAVA_HOME/conf/security/java.security`.

---

#### 🟡 Playbook 14: Ephemeral Disk Space Exhaustion via Upload Temp Files
- **Severity:** `SEV-2` | **Domain:** `Storage / Web`
- **Symptoms:** Kubernetes pods evicted with `DiskPressure` / `No space left on device` during batch file processing.
- **Diagnostic Command:**
  ```bash
  df -h /tmp
  ls -lh /tmp | head -n 20
  ```
- **Root Cause:** Multipart file uploads created un-deleted temporary files on container local storage.
- **Immediate Mitigation:**
  ```bash
  find /tmp -type f -name "upload_*" -mmin +60 -delete
  ```
- **Permanent Remediation:** Enforce streaming uploads (8KB chunks) and delete temp files in deterministic `try-finally` blocks.

---

#### 🟡 Playbook 15: ShedLock Distributed Job Overlap Under Network Partition
- **Severity:** `SEV-2` | **Domain:** `Scheduling / Async`
- **Symptoms:** Billing batch job executed simultaneously on two pods, double-billing 10,000 customers.
- **Diagnostic Command:**
  ```sql
  SELECT * FROM shedlock WHERE name = 'BillingBatchJob';
  ```
- **Root Cause:** `lockAtLeastFor` was omitted or set to 0, allowing another pod to acquire the lock immediately during a clock jump.
- **Immediate Mitigation:** Cancel duplicate batch jobs and issue credit refunds for double-billed accounts.
- **Permanent Remediation:** Configure `lockAtLeastFor = "PT5M"` to absorb clock skew and `lockAtMostFor = "PT15M"` to guard against crashes.

---

#### 🔴 Playbook 16: RabbitMQ Memory Alarm Trigger & Unacked Message Flood
- **Severity:** `SEV-1` | **Domain:** `Messaging`
- **Symptoms:** RabbitMQ blocks all message publishers; API requests timeout with 504 Gateway Timeout.
- **Diagnostic Command:**
  ```bash
  rabbitmqctl list_queues name messages_unacknowledged consumers memory
  ```
- **Root Cause:** Consumer swallowed exceptions without ACK/NACK; unacknowledged message backlog exceeded RabbitMQ memory threshold.
- **Immediate Mitigation:**
  > [!WARNING]
  > ⚠️ Do not run blindly in production: Purging unacknowledged queues can drop messages if not backed up.
  ```bash
  kubectl scale deployment/rabbitmq-consumers --replicas=20
  ```
- **Permanent Remediation:** Configure strict `basicQos(100)` prefetch limits and Dead Letter Exchange routing.

---

#### 🔴 Playbook 17: Microservice Cascading Thread Pool Exhaustion
- **Severity:** `SEV-1` | **Domain:** `Resilience`
- **Symptoms:** Slow downstream fraud API (5s latency) exhausts Tomcat worker threads; unrelated payment APIs stop responding.
- **Diagnostic Command:**
  ```bash
  jcmd <PID> Thread.print | grep -i "http-nio" | grep "TIMED_WAITING" | wc -l
  ```
- **Root Cause:** Lack of bulkhead isolation and timeout policies allowed a slow dependency to monopolize the global thread pool.
- **Immediate Mitigation:** Enable circuit breaker fallback immediately via configuration toggle.
- **Permanent Remediation:** Implement Resilience4j Bulkhead and CircuitBreaker with a strict 1.5s timeout.

---

#### 🔴 Playbook 18: CPU 100% via Regex Catastrophic Backtracking
- **Severity:** `SEV-1` | **Domain:** `Core Runtime`
- **Symptoms:** All container CPU cores spike to 100%; thread dump shows multiple threads stuck in `java.util.regex.Pattern$Loop.match`.
- **Diagnostic Command:**
  ```bash
  top -H -p <PID>
  jcmd <PID> Thread.print | grep -A 10 "Pattern.matcher"
  ```
- **Root Cause:** Flawed regular expression with nested quantifiers (e.g. `(a+)+$`) evaluated on crafted malicious user input.
- **Immediate Mitigation:** Block malicious input pattern at Web Application Firewall (WAF) level.
- **Permanent Remediation:** Refactor regex using possessive quantifiers (`++`) or atomic grouping, and enforce input length limits.

---

#### 🔴 Playbook 19: Kubernetes Readiness Probe Flapping Blackout
- **Severity:** `SEV-1` | **Domain:** `Kubernetes`
- **Symptoms:** All pods removed from Service endpoints simultaneously; API Gateway returns 503 Service Unavailable.
- **Diagnostic Command:**
  ```bash
  kubectl describe service/finflow-api | grep -i "Endpoints"
  ```
- **Root Cause:** Readiness probe executed heavy SQL queries against the database; database slowdown caused all probes to fail concurrently.
- **Immediate Mitigation:**
  ```bash
  # Temporarily relax probe timeout
  kubectl patch deployment finflow-api -p '{"spec":{"template":{"spec":{"containers":[{"name":"api","readinessProbe":{"timeoutSeconds":10,"failureThreshold":5}}]}}}}'
  ```
- **Permanent Remediation:** Separate internal health probes (`/actuator/health/readiness`) from external infrastructure dependency checks.

---

#### 🔴 Playbook 20: Out-of-Order Kafka Consumption Ledger Corruption
- **Severity:** `SEV-1` | **Domain:** `Kafka / Data Integrity`
- **Symptoms:** Customer account balance negative; withdrawal event processed before initial deposit due to partition hopping.
- **Diagnostic Command:**
  ```sql
  SELECT account_id, balance FROM accounts WHERE balance < 0;
  ```
- **Root Cause:** Producer published messages with null/random keys, distributing transactions for the same account across multiple partitions.
- **Immediate Mitigation:** Freeze affected accounts and run chronological ledger replay rebuild.
- **Permanent Remediation:** Enforce partition key hashing on `accountId` (`kafkaTemplate.send(topic, accountId, event)`).

---

### 6. Master Diagnostic Command Cheat Sheet

```bash
# 1. Capture JVM Thread Dump & High CPU Threads
top -H -p <PID>
jcmd <PID> Thread.print > /tmp/thread_dump.tdump

# 2. Capture JVM Native Memory Tracking Baseline & Summary
jcmd <PID> VM.native_memory baseline
jcmd <PID> VM.native_memory detail.diff

# 3. Inspect PostgreSQL Blocking Locks & Transactions
SELECT pid, usename, state, age(clock_timestamp(), query_start), query 
FROM pg_stat_activity 
WHERE state != 'idle' AND age(clock_timestamp(), query_start) > interval '5 seconds';

# 4. Check Kubernetes Pod Restarts & OOMKills
kubectl get pods -n production --sort-by='.status.containerStatuses[0].restartCount'
kubectl describe pod <POD_NAME> | grep -E "OOMKilled|Exit Code|Terminated"

# 5. Inspect Linux Kernel OOM Invocations
dmesg -T | grep -i -E "oom-killer|out of memory|killed process"
```

---

### 7. Step-by-Step Incident Command Runbook

```text
Step 1: Declare Incident & Establish Command.
        Incident Commander establishes audio bridge/war room; Scribe starts incident timeline.

Step 2: Assess Blast Radius & Stabilize Platform.
        Prioritize user traffic restoration over root cause discovery. Execute rollback, circuit breaker toggle, or traffic diversion.

Step 3: Preserve Diagnostic Forensics.
        Trigger heap dump (`jcmd <PID> GC.heap_dump`), capture thread dump, and dump `pg_stat_activity` before terminating pods.

Step 4: Execute Verified Mitigation Runbook.
        Apply pre-tested operational fix (e.g. `MALLOC_ARENA_MAX=2`, `pg_terminate_backend`, Kafka consumer scale-out).

Step 5: Validate Recovery & Stand Down War Room.
        Verify error rates drop to baseline ($<0.1\%$), P99 latency stabilizes, and synthetic checks pass. Transition to Blameless Post-Mortem.
```

---

### 8. Technical Root Cause Analysis (RCA) & Post-Mortem Template

#### The 5 Whys Framework
1. **Why did the banking API crash?** HikariCP connection pool ran out of connections.
2. **Why were connections exhausted?** All connections were blocked waiting for a lock on the `accounts` table.
3. **Why was the table locked?** A Flyway migration script executed `ALTER TABLE` during peak hours.
4. **Why did the ALTER TABLE block for 45 minutes?** It lacked `SET lock_timeout = '2s'` and queued behind an un-indexed reporting query.
5. **Why was un-timed DDL deployed directly to production?** The CI/CD pipeline lacked automated SQL migration linting rules.

#### Post-Mortem Action Items (SMART Criteria)
- **Preventative:** Add ArchUnit / SQLFluff migration linter to GitHub Actions CI to enforce `lock_timeout`.
- **Detective:** Configure Prometheus alert on `HikariCP_pending_threads > 10` for $>30$ seconds.
- **Mitigative:** Update SRE runbook with automated `pg_terminate_backend` kill switch.

---

### 9. Production-Grade Fixes

#### ✅ Fix: Incident Triage Engine (`IncidentTriageEngine.java`)
```java
@Service
public class IncidentTriageEngine {

    public TriageDecision triageAlert(double errorRatePercent, double p99LatencyMs, boolean isRevenueImpacting) {
        String severity = (isRevenueImpacting || errorRatePercent > 5.0 || p99LatencyMs > 2000.0) 
                ? "SEV1_CRITICAL" : (errorRatePercent > 1.0) ? "SEV2_MAJOR" : "SEV3_MINOR";
        int slaMinutes = "SEV1_CRITICAL".equals(severity) ? 15 : 45;
        return new TriageDecision(severity, slaMinutes, "Open War Room; Execute Runbook", getAllScenarios());
    }
}
```

---

### 10. Verification

1. **Triage Severity Test:** Run `IncidentTriageEngineTest.java` to verify SLA and severity classification.
2. **20-Scenario Catalog Test:** Run `IncidentCatalogCoverageTest.java` to verify 100% playbook coverage across all 20 incident archetypes.
3. **Controller API Test:** Run `IncidentResponseControllerTest.java` to test REST triage and playbook lookup endpoints.
4. **Integration Test:** Run `Module28IntegrationTest.java` to verify Spring Boot context and Actuator health.

---

### 11. Prevention & Production Readiness

1. **Rule: Mitigate First, Root-Cause Later:**
   Never spend hours debugging a live SEV-1 outage while customers suffer; rollback or divert traffic immediately.
2. **Rule: Always Preserve Forensics Before Killing Pods:**
   Capture thread dumps and heap dumps before terminating stuck containers.
3. **Master Incident SLO Alert Rule:**
```yaml
- alert: Sev1ProductionOutage
  expr: (sum(rate(http_requests_total{status=~"5.."}[2m])) / sum(rate(http_requests_total[2m]))) > 0.05
  for: 1m
  labels:
    severity: page
  annotations:
    summary: "SEV-1 CRITICAL OUTAGE: HTTP 5xx error rate exceeds 5% on production cluster"
```

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What is the primary role of an Incident Commander during a SEV-1 outage?**
   *Answer:* The Incident Commander leads the response, maintains operational command, delegates diagnostic tasks, silences non-productive debates, coordinates communication, and ensures the team focuses on *rapid customer mitigation* rather than lengthy root-cause investigation.
2. **Q: Why is "Mitigate First, Investigate Later" the golden rule of incident response?**
   *Answer:* During a production outage, every minute costs thousands of dollars in revenue and customer trust. The priority is restoring service (via rollback, pod scaling, traffic diversion, or feature flag kill-switches). Forensic logs and heap dumps can be analyzed post-mitigation.
3. **Q: What are the Google SRE Four Golden Signals, and how do they guide triage?**
   *Answer:* Latency (time taken to serve requests), Traffic (demand/request volume), Errors (rate of failed requests), and Saturation (how full resources are: CPU, memory, connection pools). They immediately localize whether the issue is load-related, dependency-related, or resource-exhaustion.
4. **Q: What makes a Post-Mortem truly "Blameless"?**
   *Answer:* A blameless post-mortem assumes that engineers acted in good faith with the information available. Instead of blaming human error, it focuses on systemic vulnerabilities: missing automated safeguards, inadequate monitoring, brittle tooling, and unsafe deployment pipelines.
5. **Q: What is the difference between MTTA, MTTD, and MTTR?**
   *Answer:* MTTD (Mean Time to Detect) is the time from incident onset to alert firing; MTTA (Mean Time to Acknowledge) is the time from alert to engineer response; MTTR (Mean Time to Resolve/Mitigate) is the time taken to restore normal service.

#### Production Incident Questions
1. **Incident:** A rolling deployment causes 100% of pods to fail readiness probes simultaneously. What is your first action?
   *Diagnosis:* Issue a roll back command immediately (`kubectl rollout undo deployment/api`), then inspect readiness probe logs.
2. **Incident:** PostgreSQL CPU hits 100% and HikariCP connection pool exhausts. How do you mitigate within 60 seconds?
   *Diagnosis:* Query `pg_stat_activity`, locate blocking DDL or runaway un-indexed queries, and execute `pg_terminate_backend(pid)` to immediately free the pool.
3. **Incident:** All payment requests fail with `SSLHandshakeException: PKIX path building failed`. How do you recover?
   *Diagnosis:* Expired/untrusted CA cert in JVM truststore. Import the renewed CA certificate into the cluster ConfigMap and perform a rolling pod restart.
4. **Incident:** A microservice pod is repeatedly OOMKilled, but JVM Heap is only 30% full. What do you inspect?
   *Diagnosis:* Glibc memory fragmentation or off-heap native memory leak. Check `dmesg`, configure `MALLOC_ARENA_MAX=2`, and inspect `jcmd <PID> VM.native_memory detail`.
5. **Incident:** Kafka consumer lag explodes because a consumer keeps rebalancing and reprocessing the same batch. What is the fix?
   *Diagnosis:* Processing time exceeds `max.poll.interval.ms`. Reduce `max.poll.records` and switch to `CooperativeStickyAssignor`.

#### Trick Questions
1. **Trick:** Does taking a full JVM heap dump on a 32GB production container cause user-facing impact?
   *Answer:* YES! `jcmd GC.heap_dump` triggers a full Stop-The-World (STW) pause that can freeze the JVM for 10–30 seconds. On-call engineers should either pull the pod out of the load balancer pool before dumping or dump heap on a designated canary pod.
2. **Trick:** Can restarting a failing microservice cluster actually make an outage worse?
   *Answer:* YES! A full cluster restart can cause a "Cold-Start Thundering Herd" where hundreds of pods attempt to connect to the database and warm caches simultaneously, crashing PostgreSQL and downstream services. Pods should be restarted via controlled rolling updates.
3. **Trick:** If Prometheus alert fires that Kafka consumer lag is 1,000,000 messages, does that always mean consumer is broken?
   *Answer:* Not necessarily. A large upstream batch producer may have dumped 1M messages in a single second. If consumer throughput is high and lag is steadily decreasing, the system is healthy and catching up.

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
