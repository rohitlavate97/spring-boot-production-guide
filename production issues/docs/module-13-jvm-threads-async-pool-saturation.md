# Module 13: JVM Threads, Async & Thread Pool Saturation

## Issue 13.1: Thread Pool Exhaustion, Unbounded Queue OOMs, and Virtual Thread Carrier Pinning

---

### 1. Scenario

During end-of-year tax statement generation on the **FinFlow Core Reporting Engine**:
1. Over 100,000 asynchronous statement generation jobs are submitted via `@Async`.
2. The application crashes with `java.lang.OutOfMemoryError: Java heap space`. Heap dump analysis reveals 450MB of memory consumed by millions of `java.util.concurrent.FutureTask` objects sitting in an unbounded `LinkedBlockingQueue`.
3. An engineer reconfigured the executor with a bounded queue of size 500, but left the default rejection policy (`AbortPolicy`). Under the next traffic burst, thousands of customer requests crashed with `RejectedExecutionException: Task rejected from ThreadPoolExecutor`.
4. After enabling Java 21 Virtual Threads (`spring.threads.virtual.enabled: true`), the JVM experienced massive thread latency because legacy `synchronized` blocks **pinned carrier threads**, preventing virtual threads from unmounting during database and network I/O.

---

### 2. Symptoms

```text
1. Rejected Execution Exceptions:
   java.util.concurrent.RejectedExecutionException: Task java.util.concurrent.FutureTask@... 
   rejected from java.util.concurrent.ThreadPoolExecutor[Running, pool size = 4, active threads = 4, queued tasks = 2, completed tasks = 142]
2. JVM Heap Memory Exhaustion:
   java.lang.OutOfMemoryError: Java heap space caused by unbounded queue accumulation.
3. Thread Creation Failures:
   java.lang.OutOfMemoryError: unable to create native thread (OS thread limit reached / max user processes limit).
4. Carrier Thread Pinning Warnings (Java 21):
   Pinned thread: java.lang.VirtualThread[#45]/runnable@ForkJoinPool-1-worker-2
      at com.finflow.troubleshooting.module13.service.VirtualThreadPinningService.executeSynchronizedTask
5. Severe Context-Switching Overhead:
   High CPU usage with low business throughput due to thousands of unmanaged OS platform threads.
```

---

### 3. Possible Root Causes

1. **The Unbounded Queue Trap (`LinkedBlockingQueue`):** Using an unconstrained queue (`queueCapacity: Integer.MAX_VALUE`) causes tasks to queue endlessly, preventing the executor from scaling from `corePoolSize` to `maxPoolSize` and eventually exhausting JVM heap memory.
2. **Default `AbortPolicy` Dropping Tasks Without Backpressure:** When bounded queues fill up, `AbortPolicy` immediately crashes caller threads instead of slowing down the producer.
3. **Unmanaged Thread Spawning (`new Thread().start()`):** Spawning raw platform threads in business loops bypasses pooling, leading to `unable to create native thread`.
4. **Virtual Thread Carrier Pinning (`synchronized` Blocks):** In Java 21 Project Loom, executing `synchronized` blocks or native methods prevents virtual threads from unmounting from their underlying OS carrier threads during blocking I/O.

---

### 4. Architecture Context: ThreadPoolExecutor Lifecycle & Virtual Thread Mounting

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        THREAD POOL EXECUTOR TASK SUBMISSION FLOW                       │
│                                                                                        │
│  [Task Submitted to Executor]                                                          │
│           │                                                                            │
│     Active Threads < CorePoolSize?                                                     │
│           ├──► [YES] ──► Create new worker thread and execute task immediately         │
│           └──► [NO]                                                                    │
│                 ▼                                                                      │
│     Can task be inserted into WorkQueue?                                               │
│           ├──► [YES] ──► Queue task (Wait for idle core thread)                        │
│           └──► [NO]  (Queue is FULL)                                                   │
│                 ▼                                                                      │
│     Active Threads < MaxPoolSize?                                                      │
│           ├──► [YES] ──► Create new non-core worker thread and execute task            │
│           └──► [NO]  (Pool and Queue are both completely SATURATED!)                   │
│                 ▼                                                                      │
│     Execute RejectedExecutionHandler:                                                  │
│           ├── AbortPolicy      ──► 💥 Throws RejectedExecutionException                │
│           ├── CallerRunsPolicy ──► ⚡ Calling thread executes task (Applies backpressure)│
│           └── DiscardPolicy    ──► 🗑️ Silently drops task                             │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### Step 1: Trigger Unbounded Queue Heap Bloat
```java
// ❌ ANTI-PATTERN: Unbounded queue ignores maxPoolSize and bloats heap
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(2);
executor.setMaxPoolSize(20);
executor.setQueueCapacity(Integer.MAX_VALUE); // <--- Never scales to 20; queues indefinitely!
```

#### Step 2: Trigger `RejectedExecutionException` with `AbortPolicy`
```java
// ❌ ANTI-PATTERN: Tiny queue + AbortPolicy crashes callers under burst
ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
executor.setCorePoolSize(2);
executor.setMaxPoolSize(4);
executor.setQueueCapacity(2);
executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy()); // Crashes on 7th task!
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Capture Thread Dump via `jstack`
```bash
jstack <PID> > thread_dump.txt
```
**Diagnostic Thread States to Look For:**
```text
"doc-worker-1" #32 prio=5 os_prio=0 cpu=12.4ms elapsed=45.2s tid=0x00007f... nid=0x1a2b waiting on condition
   java.lang.Thread.State: TIMED_WAITING (parking)
	at jdk.internal.misc.Unsafe.park(java.base@21.0.3/Native Method)
	- parking to wait for  <0x0000000701234567> (a java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject)
	at java.util.concurrent.locks.LockSupport.parkNanos(java.base@21.0.3/LockSupport.java:269)
	at java.util.concurrent.locks.AbstractQueuedSynchronizer$ConditionObject.awaitNanos(AbstractQueuedSynchronizer.java:1758)
	at java.util.concurrent.LinkedBlockingQueue.poll(LinkedBlockingQueue.java:460)
```

#### Method 2: Detect Virtual Thread Pinning via JVM Flag
Run the JVM with:
```bash
java -Djdk.tracePinnedThreads=full -jar app.jar
```
**Stdout Output:**
```text
Thread[#45,ForkJoinPool-1-worker-2,5,CarrierThreads]
    com.finflow.troubleshooting.module13.service.VirtualThreadPinningService.executeSynchronizedTask(VirtualThreadPinningService.java:22) <== PINNED
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Inspect Actuator Executor Metrics.
        Check activeCount, poolSize, queueSize, and remainingQueueCapacity.

Step 2: Replace Unbounded Queues with Sized Bounded Queues.
        Set queueCapacity to a reasonable bound (e.g. 50 to 500) to prevent OutOfMemoryError.

Step 3: Switch Rejection Policy to CallerRunsPolicy.
        Set rejectedExecutionHandler: new ThreadPoolExecutor.CallerRunsPolicy().
        This forces the HTTP web worker thread to execute the task itself, naturally slowing down incoming HTTP submissions.

Step 4: Audit Virtual Thread Carrier Pinning.
        Search codebase for synchronized (lock) blocks wrapping blocking I/O.
        Refactor to java.util.concurrent.locks.ReentrantLock.

Step 5: Configure Graceful Executor Shutdown.
        Set setWaitForTasksToCompleteOnShutdown(true) and setAwaitTerminationSeconds(10).
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. Why `ThreadPoolExecutor` Fills the Queue Before Expanding to `maxPoolSize`
The JDK `ThreadPoolExecutor` algorithm prioritizes queuing over thread creation:
1. If `activeThreads < corePoolSize`, spawn a new thread.
2. If `activeThreads >= corePoolSize`, **attempt to put task in `workQueue`**.
3. **Only if `workQueue.offer()` returns `false` (queue is full)**, spawn a new thread up to `maxPoolSize`.
4. If `activeThreads == maxPoolSize` AND queue is full, invoke `RejectedExecutionHandler`.

**The Trap:** If `queueCapacity` is unbounded (e.g. default `Integer.MAX_VALUE`), the queue is *never full*. Therefore, `maxPoolSize` is **completely ignored**, and the pool will never run more than `corePoolSize` threads!

#### 2. Rejection Policies Compared
| Policy | Behavior | Production Impact |
|:---|:---|:---|
| **`AbortPolicy`** *(Default)* | Throws `RejectedExecutionException` | Crashes caller; loses work unless caught. |
| **`CallerRunsPolicy`** | Calling thread executes task | ✅ **Recommended**: Applies backpressure to slow down publisher. |
| **`DiscardPolicy`** | Silently drops task | ❌ Silent data loss. |
| **`DiscardOldestPolicy`** | Drops head of queue, re-attempts submit | Drops older tasks; unpredictable order. |

#### 3. Virtual Thread Carrier Pinning
- Virtual threads are lightweight user-mode threads managed by the JVM and scheduled onto a small pool of OS **Carrier Threads** (`ForkJoinPool`).
- When a virtual thread performs blocking socket or file I/O, it unmounts from its carrier thread, allowing other virtual threads to execute.
- **The Pinning Trap:** When a virtual thread enters a `synchronized` block/method or executes a native method (JNI), the thread is **pinned** to its carrier thread. If blocking I/O occurs while pinned, the underlying OS carrier thread is frozen, defeating the entire purpose of virtual threads!
- **Fix:** Replace `synchronized` with `ReentrantLock`.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Production Bounded `ThreadPoolTaskExecutor` with Backpressure
```java
@Configuration
public class ThreadPoolConfig {

    @Bean(name = "documentTaskExecutor")
    public ThreadPoolTaskExecutor documentTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100); // Bounded queue: Prevents OOM
        executor.setThreadNamePrefix("doc-worker-");

        // Applies natural backpressure by running excess tasks on producer thread
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        
        // Graceful shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }
}
```

#### ✅ Fix 2: Refactoring `synchronized` to `ReentrantLock` for Virtual Threads
```java
@Service
public class VirtualThreadSafeService {

    private final ReentrantLock lock = new ReentrantLock();

    public String processSafely(long delayMs) {
        lock.lock(); // ReentrantLock allows virtual thread unmounting during blocking calls!
        try {
            Thread.sleep(delayMs); // Virtual thread unmounts cleanly from carrier thread
            return "SUCCESS";
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "INTERRUPTED";
        } finally {
            lock.unlock();
        }
    }
}
```

#### ✅ Fix 3: Exposing Executor Metrics via Actuator
```java
@RestController
@RequestMapping("/api/v1/async")
public class AsyncTelemetryController {

    private final ThreadPoolTaskExecutor documentTaskExecutor;

    public AsyncTelemetryController(@Qualifier("documentTaskExecutor") ThreadPoolTaskExecutor executor) {
        this.documentTaskExecutor = executor;
    }

    @GetMapping("/metrics")
    public Map<String, Object> getPoolMetrics() {
        ThreadPoolExecutor pool = documentTaskExecutor.getThreadPoolExecutor();
        return Map.of(
            "activeCount", pool.getActiveCount(),
            "poolSize", pool.getPoolSize(),
            "queueSize", pool.getQueue().size(),
            "remainingQueueCapacity", pool.getQueue().remainingCapacity(),
            "completedTaskCount", pool.getCompletedTaskCount()
        );
    }
}
```

---

### 10. Verification

1. **Saturation & Backpressure Test:** Run `ThreadPoolSaturationAndRejectionTest.java` to verify that submitting 8 concurrent tasks against a 2-core, 2-queue, 4-max pool triggers `CallerRunsPolicy` without dropping tasks or throwing exceptions.
2. **Async Execution Test:** Run `AsyncExecutionCompletionTest.java` to confirm `@Async` execution on designated worker threads.
3. **Virtual Thread Unmounting Test:** Run `VirtualThreadLockUnmountingTest.java` to confirm virtual thread compatibility with `ReentrantLock`.
4. **Integration Test:** Run `Module13IntegrationTest.java` to verify async REST execution and metrics endpoints.

---

### 11. Prevention & Production Readiness

1. **Always Set a Bounded `queueCapacity`:**
   Never leave `queueCapacity` unset on custom `ThreadPoolTaskExecutor` beans.
2. **Always Configure `CallerRunsPolicy` for Async Services:**
   Avoid `AbortPolicy` unless fast-failure is an explicit architectural requirement.
3. **Audit Virtual Threads for Pinning:**
   Enable `-Djdk.tracePinnedThreads=short` in CI test runs to catch carrier thread pinning before deployment.

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: Explain the step-by-step task execution algorithm of `ThreadPoolExecutor` when a task is submitted.**
2. **Q: Why does `maxPoolSize` have zero effect when using an unbounded `LinkedBlockingQueue`?**
3. **Q: What are the four standard `RejectedExecutionHandler` policies in the JDK, and when should each be used?**
4. **Q: How do Java 21 Virtual Threads differ from traditional Java platform threads in terms of memory and scheduling?**
5. **Q: What is "Carrier Thread Pinning" in Virtual Threads, and how do you resolve it?**

#### Production Incident Questions
1. **Incident:** An async notification worker stopped processing emails. Logs show no errors. `jstack` shows 4 worker threads in `WAITING` state and the queue size is 500,000. What happened?
2. **Incident:** You upgraded a Spring Boot application to Java 21 and enabled virtual threads. Under load, response times became 10x slower. How do you diagnose carrier thread exhaustion?
3. **Incident:** A batch worker spawns `CompletableFuture.supplyAsync()` without providing a custom executor. Why does this degrade overall application performance? *(Hint: Shared `ForkJoinPool.commonPool()`!)*
4. **Incident:** An application threw `OutOfMemoryError: unable to create native thread` with only 500MB of heap used out of 8GB. What OS / JVM setting caused this?
5. **Incident:** How do you guarantee that MDC logging context (TraceId / UserId) is preserved across `@Async` task boundaries?

#### Trick Questions
1. **Trick:** If `corePoolSize = 0` and `queueCapacity = 100`, what happens when the first task is submitted?
2. **Trick:** Does `Executors.newFixedThreadPool(10)` use a bounded or unbounded queue?
3. **Trick:** Can a virtual thread run on multiple different OS carrier threads over its lifecycle?

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
