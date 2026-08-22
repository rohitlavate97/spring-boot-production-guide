# Module 22: Scheduled Jobs, Job Overlaps & Cluster Duplication

## Issue 22.1: Multi-Node Scheduled Job Duplication, Single-Threaded Scheduler Starvation, and fixedRate Overlaps

---

### 1. Scenario

During monthly subscription renewal on the **FinFlow Automated Billing & Statement Generation Engine**:
1. A recurring fee billing job was configured with standard Spring Boot `@Scheduled(cron = "0 0 1 1 * ?")` to debit $29.99 from 40,000 active customer accounts at 01:00:00 UTC on the 1st of every month.
2. Because the application was deployed across **6 Kubernetes pod replicas** behind a load balancer, **all 6 pods fired the scheduled cron job at the exact same second**.
3. Each pod independently queried the database for due accounts and executed payment debits. 40,000 customers were **charged 6 times ($179.94 instead of $29.99)**, resulting in **$7.1M in duplicate charges**, a flood of customer support tickets, and payment gateway chargeback penalties (**The Multi-Node Cluster Duplication Disaster**).
4. Earlier that week, an engineer added a data archival job (`@Scheduled(cron = "0 0 2 * * ?")`) that performed bulk SQL deletes taking **45 minutes**.
5. Because Spring Boot initializes `TaskScheduler` with a **single worker thread (`poolSize: 1`) by default**, the archival job monopolized the entire scheduler. All other scheduled jobs across the application—including critical **fraud blacklist syncs, token cleanups, and exchange rate refreshes—were completely stalled for 45 minutes** (**Single-Threaded TaskScheduler Starvation**).
6. Concurrently, a reconciliation task configured with `@Scheduled(fixedRate = 60000)` (every 60s) took 140s due to database contention, spawning overlapping concurrent executions that deadlocked the financial ledger table.

---

### 2. Symptoms

```text
1. Multi-Node Duplicate Business Operations:
   Cron jobs execute N times where N is the number of Kubernetes pod replicas.
   Duplicate emails, duplicate billing invoices, or duplicate batch reports generated simultaneously.

2. Missed & Delayed Scheduled Executions (Scheduler Starvation):
   High-frequency scheduled tasks (e.g. every 10 seconds) stop firing while a slow task is executing.
   Thread dumps show the single "scheduling-1" thread stuck in a long database read or file upload.

3. Database Deadlocks on Concurrent fixedRate Overlaps:
   Multiple instances of the same scheduled method running concurrently against identical database rows.

4. Daylight Saving Time (DST) & Timezone Shifts:
   Daily settlement job runs 1 hour early or 1 hour late after DST transitions because cron lacked zone="UTC".

5. Silent Death of Scheduler on Uncaught RuntimeExceptions:
   A scheduled task throws an unhandled RuntimeException, causing Spring's TaskScheduler to silently drop future executions.
```

---

### 3. Possible Root Causes

1. **Lack of Distributed Scheduling Coordination (Missing ShedLock):** Spring's `@Scheduled` annotation is strictly in-process. In a multi-node Kubernetes cluster, every running JVM instance executes the schedule independently.
2. **Default Single-Threaded `ThreadPoolTaskScheduler`:** Spring Boot defaults `spring.task.scheduling.pool.size = 1`. A single long-running task blocks all other scheduled tasks across the JVM.
3. **Misusing `fixedRate` Instead of `fixedDelay`:** `fixedRate` calculates the next execution time based on the *start time* of the previous execution. If task duration $> \text{fixedRate}$, queued tasks fire immediately or concurrently.
4. **Unpinned Timezones in Cron Expressions:** Relying on the host OS default timezone rather than explicitly declaring `zone = "UTC"`.
5. **Clock Skew Across Cluster Nodes:** Slight time drift (e.g. 2–5 seconds) between Kubernetes nodes causing Node B to acquire a distributed lock immediately after Node A finishes if `lockAtLeastFor` is omitted.

---

### 4. Architecture Context: Multi-Pod Cluster Scheduling & ShedLock Mechanics

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                     DISTRIBUTED SCHEDULER COORDINATION (SHEDLOCK ARCHITECTURE)                  │
│                                                                                                 │
│  [Time: 01:00:00 UTC - 6 Kubernetes Pod Replicas Fire @Scheduled Cron simultaneously]           │
│                                                                                                 │
│  Pod 1 ─────────► [Attempts: INSERT INTO shedlock (name, lock_until, locked_by) ...] ──► ACQUIRED!│
│  Pod 2 ─────────► [Attempts: INSERT INTO shedlock ...] ──► Fails (Row Locked by Pod 1) ──► SKIPPED!│
│  Pod 3 ─────────► [Attempts: INSERT INTO shedlock ...] ──► Fails (Row Locked by Pod 1) ──► SKIPPED!│
│  Pod 4 ─────────► [Attempts: INSERT INTO shedlock ...] ──► Fails (Row Locked by Pod 1) ──► SKIPPED!│
│  Pod 5 ─────────► [Attempts: INSERT INTO shedlock ...] ──► Fails (Row Locked by Pod 1) ──► SKIPPED!│
│  Pod 6 ─────────► [Attempts: INSERT INTO shedlock ...] ──► Fails (Row Locked by Pod 1) ──► SKIPPED!│
│                                                                                                 │
│  ┌───────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ POD 1 EXECUTION TIMELINE:                                                                 │  │
│  │ 1. 01:00:00 - Acquires Lock: lockAtMostFor = 30m (Safety if Pod 1 dies/OOMKilled)         │  │
│  │ 2. 01:00:00 to 01:04:15 - Executes 40,000 Account Billing Invoices                        │  │
│  │ 3. 01:04:15 - Job Finished! Updates lock_until = lockAtLeastFor = 5m (01:05:00)           │  │
│  │    (Guarantees clock skew on other pods cannot trigger duplicate execution!)               │  │
│  └───────────────────────────────────────────────────────────────────────────────────────────┘  │
│                                                                                                 │
│  Result: EXACTLY 1 BILLING CYCLE EXECUTED ACROSS THE ENTIRE CLUSTER (ZERO DUPLICATE BILLING!)   │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### ❌ Anti-Pattern 1: Unprotected `@Scheduled` in Clustered Microservice
```java
// ❌ FATAL ANTI-PATTERN: Fired by every replica pod in Kubernetes simultaneously!
@Scheduled(cron = "0 0 1 1 * ?")
public void runMonthlyBilling() {
    billingService.chargeAllActiveSubscriptions(); // Charges customers N times!
}
```

#### ❌ Anti-Pattern 2: Relying on Default Scheduler Pool Size (Single-Threaded Starvation)
```java
// ❌ ANTI-PATTERN: This 45-minute task blocks ALL other @Scheduled methods in the JVM!
@Scheduled(cron = "0 0 2 * * ?")
public void slowArchivalTask() {
    Thread.sleep(45 * 60 * 1000); // Stalls fraud checks and token cleanups!
}
```

#### ❌ Anti-Pattern 3: `fixedRate` Overlap Trap
```java
// ❌ ANTI-PATTERN: If reconciliation takes 140s, executions overlap and deadlock DB!
@Scheduled(fixedRate = 60000)
public void reconcileLedger() {
    performHeavyReconciliation(); // Takes 140s
}
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Inspect ShedLock Database Table
```sql
SELECT name, lock_until, locked_at, locked_by 
FROM shedlock;
```
**Diagnostic Output:**
```text
name               | lock_until              | locked_at               | locked_by
MonthlyBillingJob  | 2026-09-01 01:05:00.000 | 2026-09-01 01:00:00.000 | finflow-pod-replica-1
```

#### Method 2: Capture Thread Dump During Scheduler Starvation
```bash
jstack <PID> | grep -A 15 "finflow-scheduler"
```
**Diagnostic Output:**
```text
"scheduling-1" #42 prio=5 tid=0x00007f... nid=0x2b1c runnable
   java.lang.Thread.State: RUNNABLE
	at java.net.SocketInputStream.socketRead0(Native Method)
	at com.finflow.troubleshooting.module22.service.ArchivalService.archiveData(ArchivalService.java:45)
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Check If Job Fired Multiple Times in Database Logs.
        Search application logs for concurrent executions with distinct pod hostnames.

Step 2: Add ShedLock to All Cluster-Critical @Scheduled Methods.
        Add @SchedulerLock(name = "JobName", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M").

Step 3: Configure Multi-Threaded TaskScheduler.
        Set `spring.task.scheduling.pool.size = 8` and configure an ErrorHandler bean.

Step 4: Replace fixedRate with fixedDelay.
        Switch tasks susceptible to long execution times to `fixedDelay` to prevent overlaps.

Step 5: Pin Timezones on All Cron Schedules.
        Add `zone = "UTC"` to every `@Scheduled(cron = "...")`.
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. Why `poolSize = 1` Destroys Application Scheduling
- Spring Boot's `ScheduledAnnotationBeanPostProcessor` registers all `@Scheduled` methods into a `TaskScheduler`.
- If no custom `TaskScheduler` bean is defined, Spring Boot instantiates `ThreadPoolTaskScheduler` with `poolSize = 1`.
- A single shared `ScheduledThreadPoolExecutor` executes all cron, fixedRate, and fixedDelay tasks sequentially.
- If Job A takes 45 minutes to execute, Job B (scheduled every 10 seconds) will not fire until Job A completes, buffering 270 missed executions!

#### 2. ShedLock Mechanics: `lockAtMostFor` vs `lockAtLeastFor`
- **`lockAtMostFor` (e.g. 30m):** Defines the maximum lease time. If the pod holding the lock crashes, gets OOMKilled, or deadlocks, the lock expires automatically in 30 minutes, allowing another pod to take over on the next schedule.
- **`lockAtLeastFor` (e.g. 5m):** Defines the minimum hold time. If Pod 1 completes the job in 10 seconds, the lock is STILL held for 5 minutes. This protects against **clock skew across cluster nodes** (where Pod 2's clock is 3 seconds behind and might trigger the job again immediately).

#### 3. Execution Model: `fixedRate` vs `fixedDelay`
- **`fixedRate`:** $\text{Execution}_n = \text{StartTime}_0 + (n \times \text{Rate})$. If task duration exceeds rate, tasks queue up and execute back-to-back without rest.
- **`fixedDelay`:** $\text{Execution}_n = \text{EndTime}_{n-1} + \text{Delay}$. Execution $n$ NEVER starts until execution $n-1$ has fully returned, guaranteeing **zero concurrency overlap**.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Hardened Multi-Threaded `TaskScheduler` (`SchedulerConfig.java`)
```java
@Configuration
public class SchedulerConfig {

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(8); // Multi-threaded execution
        scheduler.setThreadNamePrefix("finflow-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        scheduler.setErrorHandler(ex ->
                log.error("[SCHEDULER ERROR] Uncaught exception in scheduled job: {}", ex.getMessage(), ex));
        scheduler.initialize();
        return scheduler;
    }
}
```

#### ✅ Fix 2: Cluster-Safe ShedLock Billing Job (`ResilientBillingJob.java`)
```java
@Component
public class ResilientBillingJob {

    @Scheduled(cron = "0 0 1 1 * ?", zone = "UTC")
    @SchedulerLock(name = "MonthlyBillingJob", lockAtMostFor = "PT30M", lockAtLeastFor = "PT5M")
    public void executeMonthlyBilling() {
        billingService.processMonthlySubscriptions();
    }

    // Zero-overlap reconciliation
    @Scheduled(fixedDelay = 60000)
    public void executeReconciliation() {
        reconciliationService.reconcileLedger();
    }
}
```

#### ✅ Fix 3: ShedLock Database Schema DDL (`PostgreSQL`)
```sql
CREATE TABLE shedlock (
    name VARCHAR(64) NOT NULL,
    lock_until TIMESTAMP NOT NULL,
    locked_at TIMESTAMP NOT NULL,
    locked_by VARCHAR(255) NOT NULL,
    PRIMARY KEY (name)
);
```

---

### 10. Verification

1. **Cluster Deduplication Test:** Run `ShedLockClusterDeduplicationTest.java` to verify that 6 concurrent cluster nodes triggering scheduled billing results in EXACTLY 1 execution.
2. **Scheduler Starvation Test:** Run `SchedulerPoolStarvationTest.java` to verify that a slow blocking job does not starve other scheduled tasks.
3. **Controller API Test:** Run `SchedulerDiagnosticsControllerTest.java` to test scheduler diagnostics and simulation endpoints.
4. **Integration Test:** Run `Module22IntegrationTest.java` to verify Spring Boot context and Actuator health.

---

### 11. Prevention & Production Readiness

1. **Rule: Always Use Distributed Locks on Cluster `@Scheduled` Methods:**
   Never deploy standard `@Scheduled` jobs across multiple replicas without ShedLock or Quartz.
2. **Rule: Always Size `TaskScheduler` Pool Size $\ge 4$:**
   Configure `spring.task.scheduling.pool.size` to prevent single-task scheduler starvation.
3. **Prometheus Alerting Rule for Scheduler Execution Delays:**
```yaml
- alert: ScheduledJobExecutionDelayed
  expr: rate(spring_scheduled_execution_seconds_max[5m]) > 300
  for: 3m
  labels:
    severity: warning
  annotations:
    summary: "Scheduled job {{ $labels.task }} duration exceeded 300s, potential scheduler bottleneck"
```

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: Why does standard Spring `@Scheduled` cause duplicate executions in a Kubernetes cluster?**
   *Answer:* `@Scheduled` is purely in-memory and local to a single JVM. When a Spring Boot microservice is scaled to N replicas, every replica runs its own scheduler instance and triggers the scheduled job simultaneously unless a distributed coordination mechanism like ShedLock is used.
2. **Q: What is the purpose of `lockAtLeastFor` in ShedLock?**
   *Answer:* `lockAtLeastFor` ensures the lock remains held for a minimum duration even if the job finishes in a few milliseconds. This protects against clock drift across cluster nodes (e.g. Node B's clock is 2 seconds behind Node A and might acquire the lock immediately after Node A releases it).
3. **Q: What is the difference between `fixedRate` and `fixedDelay` in Spring scheduling?**
   *Answer:* `fixedRate` measures the interval from the *start* of the previous execution (which can lead to concurrent overlapping executions if the task runs longer than the rate). `fixedDelay` measures the interval from the *completion* of the previous execution, guaranteeing sequential, non-overlapping runs.
4. **Q: What happens if an uncaught `RuntimeException` is thrown inside a `@Scheduled` method?**
   *Answer:* By default, the single worker thread logs the exception and may suppress future executions of that task depending on the scheduler implementation. Configuring an explicit `ErrorHandler` on `ThreadPoolTaskScheduler` ensures exceptions are caught and future schedules continue.
5. **Q: Why is `zone = "UTC"` mandatory on financial cron expressions?**
   *Answer:* Without an explicit timezone, Spring uses the host system's default timezone. Daylight Saving Time (DST) changes can cause daily cron jobs to run twice (fall back) or be skipped entirely (spring forward).

#### Production Incident Questions
1. **Incident:** 50,000 customers received duplicate monthly subscription invoices at 00:00 UTC. What was the root cause?
   *Diagnosis:* The application was running on 4 Kubernetes pods without ShedLock. All 4 pods executed the `@Scheduled(cron = ...)` billing method simultaneously. Fix: Integrate ShedLock with `@SchedulerLock`.
2. **Incident:** A microservice's token cleanup and metrics jobs stopped running whenever an hourly data export started. Why?
   *Diagnosis:* Single-threaded `TaskScheduler` starvation (`poolSize = 1`). The data export blocked the single scheduler worker thread. Fix: Configure `ThreadPoolTaskScheduler` with `poolSize = 8`.
3. **Incident:** An inventory reconciliation job deadlocks every night at 03:00 AM. Logs show multiple threads executing the same reconciliation method. Why?
   *Diagnosis:* The job was configured with `fixedRate = 10000` (10s), but execution took 45 seconds under night-time DB load, spawning overlapping executions. Fix: Switch to `fixedDelay = 10000`.
4. **Incident:** A pod holding a ShedLock was killed by Kubernetes OOMKiller. Why did the job fail to run on the other pods?
   *Diagnosis:* `lockAtMostFor` was either omitted or set too high (e.g. 24 hours), causing other pods to skip the job until the lease expired. Fix: Set `lockAtMostFor` to a reasonable bound (e.g. `PT30M`).
5. **Incident:** A scheduled report ran at 01:00 AM in winter and 02:00 AM in summer. How do you fix it?
   *Diagnosis:* Cron schedule lacked timezone pinning. Fix: Add `zone = "UTC"` to the `@Scheduled` annotation.

#### Trick Questions
1. **Trick:** Does `@Async` make a `@Scheduled(fixedDelay = 5000)` job non-overlapping?
   *Answer:* No! Adding `@Async` causes the scheduler thread to return immediately after launching a worker thread, effectively breaking the `fixedDelay` contract and causing overlapping executions if the async task takes longer than 5 seconds!
2. **Trick:** Does ShedLock prevent multiple threads *within the same JVM* from executing a scheduled method?
   *Answer:* Yes. ShedLock manages both local in-JVM synchronization and distributed database locks.
3. **Trick:** If a cron job is configured with `0 0 12 * * ?` without `zone`, will it run at 12:00 UTC?
   *Answer:* Only if the server's OS timezone is UTC. If the server is in US/Eastern (`UTC-5`), it will run at 17:00 UTC.

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
