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

### 4. The 20 Comprehensive Production Incident Playbooks

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                      CATALOG OF 20 ENTERPRISE INCIDENT PLAYBOOKS                                │
├────┬─────────────────────────────────────────────────┬──────────┬───────────────────────────────┤
│ ID │ Incident Scenario Title                         │ Severity │ Failure Domain / Module Ref   │
├────┼─────────────────────────────────────────────────┼──────────┼───────────────────────────────┤
│ 01 │ JVM Native Memory Leak & Glibc Fragmentation    │ SEV-1    │ JVM Memory (Module 01)        │
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
│ 19 │ Kubernetes Readiness Probe Flapping Blackout    │ SEV-1    │ Kubernetes (Module 14)        │
│ 20 │ Out-of-Order Kafka Consumption Ledger Corruption│ SEV-1    │ Kafka Streaming (Module 20)   │
└────┴─────────────────────────────────────────────────┴──────────┴───────────────────────────────┘
```

---

### 5. Detailed Runbook Deep-Dive for Key Incident Archetypes

#### Playbook 01: JVM Native Memory Leak & Glibc Arena Fragmentation
- **Root Cause:** Glibc `malloc` creates up to $8 \times \text{CPU cores}$ memory arenas. High thread turnover causes extreme C-heap memory fragmentation, triggering Linux `OOMKiller` while JVM Heap remains $<50\%$ utilized.
- **Immediate Mitigation:**
  ```bash
  # Inject environment variable to cap glibc arenas to 2
  kubectl set env deployment/finflow-clearing MALLOC_ARENA_MAX=2
  ```
- **Permanent Remediation:** Switch Docker base image to `jemalloc` (`LD_PRELOAD=/usr/lib/libjemalloc.so`) and configure Native Memory Tracking (`-XX:NativeMemoryTracking=detail`).

#### Playbook 02: PostgreSQL AccessExclusiveLock Cascading Pool Exhaustion
- **Root Cause:** `ALTER TABLE accounts ADD COLUMN tier VARCHAR NOT NULL DEFAULT 'STD'` without `lock_timeout` queued behind a slow analytical query, blocking all subsequent `SELECT` and `UPDATE` statements.
- **Immediate Mitigation:**
  ```sql
  -- Identify and terminate the blocking DDL or query immediately
  SELECT pg_terminate_backend(blocking_locks.pid)
  FROM pg_catalog.pg_locks blocked_locks
  JOIN pg_catalog.pg_locks blocking_locks ON blocking_locks.relation = blocked_locks.relation
  WHERE NOT blocked_locks.granted;
  ```
- **Permanent Remediation:** Enforce `SET lock_timeout = '2s';` in all Flyway migration scripts.

#### Playbook 03: Kafka Consumer Lag Rebalance Death Spiral
- **Root Cause:** Batch processing time exceeded `max.poll.interval.ms` (300s). The broker marked the consumer dead and triggered group rebalance, halting all processing.
- **Immediate Mitigation:**
  ```bash
  # Scale consumer pod replicas to distribute partition load
  kubectl scale deployment/kafka-consumer-payments --replicas=12
  ```
- **Permanent Remediation:** Reduce `max.poll.records` to 100, increase `max.poll.interval.ms` to 600s, and switch to `CooperativeStickyAssignor`.

#### Playbook 11: Distributed Partial Failure & Missing Saga Compensations
- **Root Cause:** Synchronous REST calls across 4 microservices failed at Step 3 (FX provider 503). The process terminated without refunding the customer's debited $5,000.
- **Immediate Mitigation:** Run manual ledger reconciliation script to credit customer wallet balances for orphaned orders.
- **Permanent Remediation:** Implement `PaymentSagaOrchestrator` with automated reverse compensation (`Refund Wallet -> Cancel Order`).

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
