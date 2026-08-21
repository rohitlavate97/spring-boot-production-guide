# Module 13: JVM Threads, Async & Thread Pool Saturation

## Overview
This module explores JVM concurrency mechanics, `ThreadPoolTaskExecutor` saturation, rejection policies (`CallerRunsPolicy` vs `AbortPolicy`), the unbounded queue OutOfMemoryError trap, and Java 21 Virtual Threads (Project Loom) carrier thread pinning.

## Key Scenarios Covered
1. **Thread Pool Saturation & Rejection Policies:**
   - Why default `AbortPolicy` drops tasks with `RejectedExecutionException`, and how `CallerRunsPolicy` introduces natural backpressure to slow down publishers.
2. **The Unbounded Queue Trap (`LinkedBlockingQueue`):**
   - Why `queueCapacity: Integer.MAX_VALUE` prevents pool expansion from `corePoolSize` to `maxPoolSize` and eventually exhausts JVM heap.
3. **Thread Dump Analysis (`jstack`):**
   - Diagnosing thread lifecycle states: `RUNNABLE`, `BLOCKED`, `WAITING`, `TIMED_WAITING`.
4. **Java 21 Virtual Threads & Carrier Pinning:**
   - How `synchronized` blocks pin OS carrier threads during blocking I/O, and why `ReentrantLock` allows virtual threads to unmount freely.

## Project Structure
- `src/main/java/.../config/`: `ThreadPoolConfig.java` (`ThreadPoolTaskExecutor` with bounded queue and `CallerRunsPolicy`).
- `src/main/java/.../service/`: `DocumentGenerationService.java` (`@Async`), `VirtualThreadPinningService.java`.
- `src/main/java/.../controller/`: `AsyncTelemetryController.java`.
- `src/test/java/.../`:
  - `ThreadPoolSaturationAndRejectionTest.java`
  - `AsyncExecutionCompletionTest.java`
  - `VirtualThreadLockUnmountingTest.java`
  - `Module13IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full 12-section technical incident guide, see [Module 13 Documentation](../../docs/module-13-jvm-threads-async-pool-saturation.md).
