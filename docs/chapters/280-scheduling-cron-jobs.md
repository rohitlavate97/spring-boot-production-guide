---
chapter: 280
topic: Scheduling & Cron Jobs — @Scheduled Internals, Distributed Locking (ShedLock), Leader Election
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270]
reference_system_node: Payment Service & Daily Settlement Engine ↔ Cron Scheduler & Distributed Lock Manager (TaskScheduler, ThreadPoolTaskScheduler, ShedLock, @SchedulerLock, lockAtMostFor, lockAtLeastFor)
---

# Chapter 280: Scheduling & Cron Jobs — @Scheduled Internals, Distributed Locking (ShedLock), Leader Election

## 1. Concept

In monolithic single-instance applications, Spring's `@Scheduled` annotation provides a simple mechanism for executing recurring tasks (e.g. hourly reconciliation, daily merchant settlements, cleanup jobs).

However, in modern cloud-native architectures where microservices scale across **multiple Kubernetes pods**, naive `@Scheduled` tasks lead to disaster: **every active pod executes the scheduled job simultaneously**. In financial platforms like FinFlow, running a midnight payout batch without coordination across 8 pods generates 8 duplicate ACH withdrawal files, resulting in catastrophic multi-million dollar overpayments.

```
+-------------------------------------------------------------------------------------------------+
|                                 The Golden Rules of Production Scheduling                       |
|                                                                                                 |
|  1. Never Run @Scheduled in Multi-Pod Environments without Distributed Locking: Always use      |
|     ShedLock (@SchedulerLock) or Kubernetes Leader Election to guarantee single-pod execution. |
|  2. Always Specify Both lockAtMostFor AND lockAtLeastFor:                                      |
|     • lockAtMostFor: Prevents permanent deadlocks if the executing pod crashes.                 |
|     • lockAtLeastFor: Prevents duplicate executions caused by fast tasks and node clock skew.  |
|  3. Never Rely on the Default TaskScheduler: Spring Boot defaults to poolSize = 1; always      |
|     configure an explicit ThreadPoolTaskScheduler (poolSize >= 5) to prevent Head-of-Line      |
|     task starvation.                                                                            |
|  4. Use Database Clock: Configure usingDbTime() in ShedLock to neutralize pod clock drift.      |
+-------------------------------------------------------------------------------------------------+
```

---

## 2. Internal Working

### Spring Scheduling Architecture & Head-of-Line Blocking

```
Spring Application Context Startup
         │
         ▼
ScheduledAnnotationBeanPostProcessor
         │
         ├── Scans beans for @Scheduled methods
         ├── Parses cron / fixedRate / fixedDelay
         │
         ▼
TaskScheduler (ThreadPoolTaskScheduler)
         │
         ├── Default: poolSize = 1 (SINGLE WORKER THREAD!)
         │     ├── Task A (Daily Settlement, takes 10 mins) ──► BLOCKS WORKER THREAD!
         │     └── Task B (Health Check, runs every 5s)     ──► STARVED FOR 10 MINUTES!
         │
         └── Production: poolSize = 10 (Multi-threaded pool)
               ├── Thread 1: Executes Task A
               └── Thread 2: Executes Task B concurrently!
```

---

### FixedRate vs FixedDelay vs Cron

| Schedule Type | Definition | Behavior when Task Duration > Configured Interval |
|---|---|---|
| **`fixedRate = 5000`** | Starts every 5,000ms from the beginning of the previous run. | **Task Backlog Hazard**: Subsequent runs queue up and fire immediately back-to-back, potentially exhausting thread pools. |
| **`fixedDelay = 5000`**| Waits 5,000ms *after* the previous execution finishes. | **Safe & Sequential**: Ensures a guaranteed quiet period between runs regardless of task execution duration. |
| **`cron = "0 0 2 * * ?"`**| Fires at explicit calendar intervals (02:00:00 UTC). | Skips if a previous execution on the same thread is still running; fires immediately on the next scheduled tick. |

---

### Distributed Locking with ShedLock

ShedLock coordinates scheduled tasks across multiple application instances using an external database table (`shedlock`):

```
┌─────────────────────────────────────────────────────────────────────────┐
│                           Database Table: shedlock                      │
│                                                                         │
│  name (VARCHAR 64, PK) │ lock_until (TIMESTAMP) │ locked_at │ locked_by│
│  ──────────────────────┼────────────────────────┼───────────┼──────────│
│  dailySettlementTask   │ 2026-08-20 02:15:00    │ 02:00:00  │ pod-1    │
└─────────────────────────────────────────────────────────────────────────┘
```

#### Atomic SQL Lock Acquisition Query
When Pod 1 and Pod 2 attempt to execute at 02:00:00 UTC, both issue an atomic SQL update:

```sql
UPDATE shedlock
SET lock_until = :newLockUntil, locked_at = :now, locked_by = :podName
WHERE name = :taskName AND lock_until <= :now;
```

- **Pod 1**: Finds `lock_until <= :now`, updates 1 row $\to$ **Acquires Lock** $\to$ Executes task.
- **Pod 2**: Finds `lock_until > :now`, updates 0 rows $\to$ **Lock Denied** $\to$ Skips task execution safely.

---

### The Mathematical Safety Envelope: `lockAtMostFor` vs `lockAtLeastFor`

```
02:00:00                                   02:00:05                  02:15:00
Cron Tick                                Task Finishes             Lock Timeout
   │                                           │                         │
   ├───────────────────────────────────────────┤                         │
   │           Actual Execution (5s)           │                         │
   │                                                                     │
   ├─────────────────────────────────────────────────────────────────────┤
   │                  lockAtMostFor = "PT15M" (15 minutes)               │
   │                  (Deadlock Prevention if Pod 1 Crashes)             │
   │                                                                     │
   ├───────────────────────────────┤                                     │
   │   lockAtLeastFor = "PT30S"    │                                     │
   │   (Clock Skew Defense)        │                                     │
```

1. **`lockAtMostFor`**: The maximum time the lock is held. If Pod 1 suffers an unrecoverable SIGKILL or hardware crash, the lock expires automatically after 15 minutes, allowing subsequent cron ticks to proceed without administrative intervention.
2. **`lockAtLeastFor`**: The minimum time the lock must remain held, even if the task completes in 100 milliseconds. This prevents a fast task on Pod 1 from releasing the lock while Pod 2's slightly drifted clock (e.g. $+500\text{ ms}$) is still evaluating the 02:00:00 cron trigger window!

---

## 3. Enterprise Scenario: FinFlow Midnight Daily Settlement Engine

In the **FinFlow Reference Architecture**:

```
Kubernetes Cluster (8 Pods of payment-service)
       │
       ├── 02:00:00 UTC Cron Trigger Fires Simultaneously on all 8 Pods
       │
       ▼
ShedLock Distributed Lock Manager (shedlock table in PostgreSQL)
       │
       ├── Pod 1: Acquires lock for "dailySettlementTask" ──► Executes $14.2M Settlement
       │
       └── Pods 2–8: Lock acquisition rejected ──► Safely log and skip execution
```

- **Batch Size**: 1,250 merchants.
- **Settlement Volume**: \$14,250,000.00 daily.
- **SLA**: Exactly-Once cluster execution per calendar day.

---

## 4. Incorrect Implementation

Below is an unhardened scheduling service demonstrating multi-pod duplicate executions and thread starvation:

```java
package com.finflow.chapter280.incorrect;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Missing @SchedulerLock -> All running pods execute the midnight cron job simultaneously!
 * 2. Unconfigured TaskScheduler uses poolSize=1 -> Long tasks starve short tasks.
 */
@Service
public class SettlementSchedulerServiceIncorrect {

    private final AtomicInteger duplicateExecutionCount = new AtomicInteger(0);

    /**
     * Anti-Pattern: @Scheduled without @SchedulerLock across multiple pods.
     * When 8 pods run this method at midnight, all 8 execute the settlement job!
     */
    public void executeUnlockedCronAcrossPods(String podName) {
        // Multi-pod disaster: 8 pods process the same settlement batch!
        duplicateExecutionCount.incrementAndGet();
    }
}
```

---

## 5. Production Incident

### Incident Timeline

| Time (UTC) | Event / Telemetry |
|---|---|
| **00:00:00** | DevOps scales the `payment-service` deployment from 1 replica to 8 replicas to handle Black Friday load. |
| **02:00:00** | The daily midnight settlement cron job (`@Scheduled(cron = "0 0 2 * * ?")`) fires on all 8 Kubernetes pods simultaneously. |
| **02:00:01** | Because the service lacked distributed locking, all 8 pods begin generating and transmitting ACH withdrawal batches to the clearing house. |
| **02:05:00** | The clearing house accepts all 8 batches. Each of the 1,250 merchants is debited **8 times**, generating **$113.6M** in unauthorized debits instead of the intended $14.2M. |
| **02:30:00** | Merchant support lines are flooded with overdraft and double-debit alerts. SEV-0 declared. |
| **03:00:00** | Treasury team initiates emergency ACH reversal files with the Federal Reserve, incurring $180,000 in batch reversal penalties. |
| **03:30:00** | Engineers implement ShedLock with `JdbcTemplateLockProvider`, `@SchedulerLock`, and `lockAtLeastFor="PT30S"`. |
| **04:00:00** | Verified in staging: 8 pods triggered simultaneously, exactly 1 pod executes, 7 pods skip. Outage resolved. |

---

## 6. Logs & Diagnostics

### 1. ShedLock Successful Lock Acquisition Log (Pod 1)
```text
2026-08-20T02:00:00.014Z INFO [payment-service,pod=pod-1] 1 --- [scheduled-task-pool-1] c.f.c.c.SettlementSchedulerServiceCorrect : ShedLock acquired. Executing daily settlement batch on thread: scheduled-task-pool-1
```

### 2. ShedLock Rejection Logs (Pods 2–8)
```text
2026-08-20T02:00:00.018Z DEBUG [payment-service,pod=pod-2] 1 --- [scheduled-task-pool-1] n.j.s.c.DefaultLockingTaskExecutor         : Not executing [dailySettlementTask]. It is locked until 2026-08-20T02:15:00.000Z by pod-1
2026-08-20T02:00:00.019Z DEBUG [payment-service,pod=pod-3] 1 --- [scheduled-task-pool-1] n.j.s.c.DefaultLockingTaskExecutor         : Not executing [dailySettlementTask]. It is locked until 2026-08-20T02:15:00.000Z by pod-1
```

---

## 7. Root Cause Analysis

```
+-------------------------------------------------------------------------------------------------+
|                               Cron Outage Root Cause Chain                                      |
|                                                                                                 |
|  1. Uncoordinated Multi-Pod Deployment                                                          |
|     └── Scaling payment-service to 8 pods caused 8 identical @Scheduled triggers to fire.       |
|                                                                                                 |
|  2. Absence of Distributed Mutual Exclusion (ShedLock)                                         |
|     └── No mechanism existed to elect a single worker or coordinate lock acquisition.           |
|                                                                                                 |
|  3. 8x Duplicate ACH Transmission ($113.6M)                                                     |
|     └── All 8 pods independently generated and transmitted settlement debit batches.            |
|                                                                                                 |
|  4. Remediation: ShedLock with usingDbTime() + lockAtLeastFor + lockAtMostFor                   |
|     └── Exactly ONE pod acquires the lock in the shedlock table; 7 pods safely skip.            |
+-------------------------------------------------------------------------------------------------+
```

---

## 8. Debugging Process

```
[1. Check ShedLock Table] Run: SELECT name, lock_until, locked_at, locked_by FROM shedlock;
       │
[2. Verify Pod Clock Drift] Check NTP synchronization across Kubernetes worker nodes
       │
[3. Inspect ThreadPoolTaskScheduler] Verify poolSize >= 5 to eliminate Head-of-Line blocking
       │
[4. Audit Lock Durations] Confirm lockAtMostFor > max task runtime AND lockAtLeastFor > cron jitter
       │
[5. Rollout] Enable @EnableSchedulerLock and verify exactly 1 execution under multi-pod load test
```

---

## 9. Correct Implementation

### 1. Scheduler Configuration with ShedLock & Thread Pool: `SchedulerConfig.java`

```java
package com.finflow.chapter280.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import javax.sql.DataSource;

@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class SchedulerConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime() // Uses database clock to avoid pod clock skew issues
                        .build()
        );
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10); // Prevents Head-of-Line task starvation
        scheduler.setThreadNamePrefix("scheduled-task-pool-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(30);
        scheduler.initialize();
        return scheduler;
    }
}
```

---

### 2. Hardened Scheduled Service: `SettlementSchedulerServiceCorrect.java`

```java
package com.finflow.chapter280.correct;

import com.finflow.chapter280.domain.SettlementBatch;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SettlementSchedulerServiceCorrect {

    private static final Logger log = LoggerFactory.getLogger(SettlementSchedulerServiceCorrect.class);

    private final AtomicInteger executionCount = new AtomicInteger(0);

    @Scheduled(cron = "0 0 2 * * ?")
    @SchedulerLock(name = "dailySettlementTask", lockAtMostFor = "PT15M", lockAtLeastFor = "PT5S")
    public SettlementBatch executeDailySettlement() {
        log.info("ShedLock acquired. Executing daily settlement batch on thread: {}", Thread.currentThread().getName());
        executionCount.incrementAndGet();

        return new SettlementBatch(
                "BATCH-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.now().toString(),
                1250,
                BigDecimal.valueOf(14_250_000.00),
                "COMPLETED",
                "pod-payment-settler-1",
                Instant.now(),
                Instant.now()
        );
    }

    public int getExecutionCount() { return executionCount.get(); }
    public void reset() { executionCount.set(0); }
}
```

---

### 3. Multi-Pod Simulation Engine: `MultiPodShedLockSimulator.java`

```java
package com.finflow.chapter280.correct;

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MultiPodShedLockSimulator {

    private final LockingTaskExecutor lockingTaskExecutor;
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger blockedCount = new AtomicInteger(0);

    public MultiPodShedLockSimulator(LockProvider lockProvider) {
        this.lockingTaskExecutor = new DefaultLockingTaskExecutor(lockProvider);
    }

    public boolean executeAsPod(String podName, String taskName, Duration lockAtMostFor, Duration lockAtLeastFor) {
        LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(),
                taskName,
                lockAtMostFor,
                lockAtLeastFor
        );

        boolean executed = false;
        try {
            lockingTaskExecutor.executeWithLock((LockingTaskExecutor.Task) () -> {
                successCount.incrementAndGet();
            }, lockConfig);
            executed = true;
        } catch (Throwable ignored) {
        }

        return executed;
    }

    public int getSuccessCount() { return successCount.get(); }
    public int getBlockedCount() { return blockedCount.get(); }
    public void reset() { successCount.set(0); blockedCount.set(0); }
}
```

---

## 10. Performance Comparison

Benchmarked across 8 Kubernetes pods running scheduled tasks.

| Metric | Unhardened Scheduler (@Scheduled Default) | Production Hardened (@Scheduled + ShedLock + ThreadPool) |
|---|---|---|
| **Multi-Pod Duplicate Executions** | 8x *(Disastrous double-settlement)* | **Exactly 1x (7 pods safely blocked)** |
| **Short Task Starvation** | Severe *(Blocked behind 10-min tasks)* | **0.00ms (Dedicated thread pool)** |
| **Pod Crash Lock Recovery Time** | Permanent Deadlock | **Automatic at `lockAtMostFor`** |
| **Clock Skew False Invocations**| Frequent | **0% (Protected by `lockAtLeastFor`)** |
| **Time Source Drift Risk** | Vulnerable (Local OS clocks) | **Immune (`usingDbTime()`)** |

---

## 11. Best Practices

### The Do's
- **DO always set `lockAtLeastFor`**: Prevents fast-running tasks from releasing the lock before other pods pass the cron trigger second.
- **DO always set `lockAtMostFor`**: Guarantees lock release if the node running the task crashes or is killed by Kubernetes OOMKilled.
- **DO configure `usingDbTime()` on `JdbcTemplateLockProvider`**: Neutralizes time drift across cloud instances.
- **DO configure `ThreadPoolTaskScheduler` with `poolSize >= 5`**: Prevents slow batch tasks from starving short-interval periodic jobs.
- **DO design scheduled batch operations to be idempotent**: Ensure secondary defense even if locks are misconfigured.

### The Don'ts
- **DON'T rely on `@Scheduled` in multi-pod deployments without distributed locking**: Causes duplicate execution on every active replica.
- **DON'T set `lockAtMostFor` shorter than maximum expected execution time**: If the task runs longer than `lockAtMostFor`, another pod will acquire the lock and execute simultaneously mid-run.
- **DON'T use `fixedRate` for tasks with variable execution times**: Use `fixedDelay` to guarantee quiet intervals between runs.
- **DON'T perform long blocking network calls without timeouts in scheduled tasks**: Holds worker threads and distributed locks indefinitely.

---

## 12. Common Mistakes

### Mistake 1: Omission of `lockAtLeastFor`
Running a task scheduled with `cron = "0 * * * * ?"` (every minute) that finishes in 5ms without `lockAtLeastFor`.
**Why it fails**: Pod 1 acquires the lock at 12:00:00.000 and finishes at 12:00:00.005, releasing the lock. Pod 2's clock is at 12:00:00.010; it sees the lock is free and executes the exact same task in the same minute!
**Production Fix**: Set `lockAtLeastFor = "PT10S"`.

### Mistake 2: The `lockAtMostFor` Split-Brain Hazard
Setting `lockAtMostFor = "PT2M"` on a task that occasionally takes 5 minutes to process large batches.
**Why it fails**: After 2 minutes, ShedLock considers Pod 1 dead and releases the lock. Pod 2 acquires the lock and begins processing while Pod 1 is still running, causing split-brain data corruption.
**Production Fix**: Set `lockAtMostFor` to $2\times\text{--}3\times$ the worst-case maximum duration.

---

## 13. Interview Questions

### Junior Tier
**Q: What is the difference between `fixedRate` and `fixedDelay` in Spring's `@Scheduled`?**
> **Answer**: 
> - `fixedRate`: Executes tasks at fixed intervals measured from the *start* time of each execution. If task execution takes longer than the interval, subsequent executions queue up and run consecutively without delay.
> - `fixedDelay`: Measures the delay strictly from the *completion* time of the previous execution, guaranteeing a consistent rest interval between successive runs.

### Mid Tier
**Q: Why does Spring's default `@Scheduled` cause Head-of-Line blocking, and how do you fix it?**
> **Answer**: By default, Spring Boot creates a single-threaded `ThreadPoolTaskScheduler` (`poolSize = 1`). All `@Scheduled` tasks in the entire application share this single thread. If Task A is a long-running batch job taking 10 minutes, all other scheduled tasks (even lightweight 5-second health checks) are blocked and starved for 10 minutes. The fix is to declare a custom `TaskScheduler` bean configuring `ThreadPoolTaskScheduler` with an explicit pool size (e.g. `poolSize = 10`).

### Senior Tier
**Q: Explain how ShedLock achieves distributed locking in a relational database, and why `lockAtLeastFor` is critical.**
> **Answer**: ShedLock uses an atomic SQL `UPDATE` statement on a shared `shedlock` table: `UPDATE shedlock SET lock_until = :until WHERE name = :name AND lock_until <= :now`. Exactly one pod succeeds in updating the row and acquiring the lock. `lockAtLeastFor` is critical because when a task finishes quickly (e.g. 10ms), ShedLock would normally release the lock immediately. If worker nodes have slight clock skew or cron evaluation delays (e.g. 50ms), a second pod evaluates the cron tick shortly after and acquires the released lock, causing duplicate execution within the same scheduled window. `lockAtLeastFor` forces the lock to remain held for a minimum duration.

### Staff Tier
**Q: Compare ShedLock with Kubernetes Leader Election (Leases) for running singleton background jobs.**
> **Answer**: 
> - **ShedLock**: Task-level distributed locking. Any pod can run any task as long as it acquires the specific task lock. Enables fine-grained concurrency where Pod A runs Task 1 and Pod B runs Task 2 simultaneously. Ideal for recurring cron jobs.
> - **Kubernetes Leader Election (Leases)**: Pod-level leadership. One pod is elected leader for the entire application deployment; all background tasks run exclusively on the active leader pod. If the leader crashes, Kubernetes automatically transfers the Lease to a standby replica after a configurable heartbeat timeout. Ideal for long-running stream consumers or daemon processes.

### Principal Tier
**Q: Design a Fault-Tolerant, Dynamic Distributed Scheduling Platform capable of scheduling 100,000 personalized merchant payout cron jobs with zero downtime.**
> **Answer**: A Principal-level architecture replaces in-process `@Scheduled` with a **Decoupled Distributed Scheduler Architecture (e.g. Quartz Cluster / Temporal / Cadence)**:
> 1. **Partitioned Job Store**: Job definitions and cron triggers are stored in a distributed PostgreSQL cluster partitioned by `merchant_id`.
> 2. **Time-Wheel / Bucket Queuing**: A dedicated Scheduler Service queries upcoming jobs in 60-second time windows and dispatches execution events into an Apache Kafka topic partitioned by `merchant_id`.
> 3. **Worker Fleet (Stateless Consumers)**: Scalable worker pods consume execution events from Kafka, process payouts with transactional idempotency, and acknowledge completion.
> 4. **Dynamic API Reconfiguration**: Merchants update their cron schedule via REST API; the scheduler updates the database trigger row immediately with zero application restarts.

---

## 14. Hands-on Exercise

### Objective
Configure a multi-threaded `TaskScheduler` and ShedLock `JdbcTemplateLockProvider` in Spring Boot:
1. Configure `ThreadPoolTaskScheduler` with `poolSize = 10`.
2. Configure ShedLock using database time (`usingDbTime()`).
3. Annotate a scheduled job with `@SchedulerLock`.

### Solution

```java
@Configuration
@EnableScheduling
@EnableSchedulerLock(defaultLockAtMostFor = "PT10M")
public class AppSchedulerConfig {

    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .usingDbTime()
                        .build()
        );
    }

    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("scheduled-pool-");
        scheduler.initialize();
        return scheduler;
    }
}
```

---

## 15. Advanced Challenge: Dynamic Database-Driven Cron Scheduler

### Enterprise Problem Statement
Implement a dynamic scheduler service that allows operators to register, update, and cancel cron jobs at runtime without application restarts.

### Enterprise Solution

```java
@Service
public class DynamicSchedulerService {

    private final TaskScheduler taskScheduler;
    private final Map<String, ScheduledFuture<?>> scheduledTasks = new ConcurrentHashMap<>();

    public DynamicSchedulerService(TaskScheduler taskScheduler) {
        this.taskScheduler = taskScheduler;
    }

    public void scheduleTask(String taskId, String cronExpression, Runnable task) {
        cancelTask(taskId); // Cancel existing schedule if present

        ScheduledFuture<?> future = taskScheduler.schedule(
                task,
                new CronTrigger(cronExpression, ZoneId.of("UTC"))
        );
        scheduledTasks.put(taskId, future);
    }

    public void cancelTask(String taskId) {
        ScheduledFuture<?> existing = scheduledTasks.remove(taskId);
        if (existing != null) {
            existing.cancel(false);
        }
    }
}
```

---

## 16. Production Checklist

Before approving any pull request involving `@Scheduled` tasks:

- [ ] **`@SchedulerLock` Configured**: Ensure all multi-pod scheduled tasks specify `@SchedulerLock`.
- [ ] **Both `lockAtMostFor` and `lockAtLeastFor` Defined**: Confirm minimum and maximum hold times are specified.
- [ ] **`usingDbTime()` Configured**: Verify `JdbcTemplateLockProvider` uses database time to neutralize node clock skew.
- [ ] **`ThreadPoolTaskScheduler` Pool Size >= 5**: Confirm custom multi-threaded scheduler is declared.
- [ ] **Graceful Shutdown Configured**: Verify `setWaitForTasksToCompleteOnShutdown(true)` is enabled.
- [ ] **Idempotent Task Design**: Ensure underlying business logic incorporates idempotency keys.
