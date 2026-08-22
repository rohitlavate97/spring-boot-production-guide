# Module 22: Scheduled Jobs, Job Overlaps & Cluster Duplication

## Overview
This module explores Spring Boot task scheduling (`@Scheduled`), solving the classic pitfalls of Single-Threaded TaskScheduler starvation, job execution overlap (`fixedRate` vs `fixedDelay`), cluster-wide duplicate job executions across multi-node microservice deployments (ShedLock distributed locking), and timezone drift.

## Key Scenarios Covered
1. **Multi-Node Cluster Duplication (Duplicate Invoicing):**
   - Why deploying standard `@Scheduled` jobs across 6 Kubernetes replicas causes every pod to execute the job simultaneously.
   - Solving cluster duplication using ShedLock distributed locking (`@SchedulerLock` with `lockAtMostFor` and `lockAtLeastFor`).
2. **Single-Threaded TaskScheduler Starvation:**
   - Why Spring Boot's default `poolSize: 1` allows a single long-running task to stall all other scheduled jobs across the application.
   - Configuring custom `ThreadPoolTaskScheduler` with thread pools, custom prefixes, and uncaught error handlers.
3. **The `fixedRate` Job Overlap Disaster:**
   - Why long-running jobs configured with `fixedRate` spawn overlapping concurrent executions that deadlock database ledgers, and why `fixedDelay` guarantees strict sequential execution.
4. **Timezone Pinning for Financial Cron Schedules:**
   - Pinning UTC timezones (`@Scheduled(cron = "...", zone = "UTC")`) to prevent daylight saving time (DST) shifts.

## Project Structure
- `src/main/java/.../config/`:
  - `SchedulerConfig.java` (Configures multi-threaded `ThreadPoolTaskScheduler`).
- `src/main/java/.../service/`:
  - `ShedLockSimulationService.java` (Implements distributed scheduler lock with clock-skew protection).
- `src/main/java/.../job/`:
  - `ResilientBillingJob.java` (Scheduled billing jobs with ShedLock and fixedDelay).
- `src/main/java/.../controller/`:
  - `SchedulerDiagnosticsController.java` (REST endpoints for scheduler stats and cluster duplication simulation).
- `src/test/java/.../`:
  - `ShedLockClusterDeduplicationTest.java`
  - `SchedulerPoolStarvationTest.java`
  - `SchedulerDiagnosticsControllerTest.java`
  - `Module22IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full deep-dive technical incident guide, see [Module 22 Documentation](../../docs/module-22-scheduled-jobs-overlaps-cluster-duplication.md).
