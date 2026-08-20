---
chapter: 290
topic: Async Processing & Thread Pools — @Async, CompletableFuture, ThreadPoolTaskExecutor, Backpressure, Virtual Threads
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270, 280]
reference_system_node: Payment Service & Merchant Statement Generator ↔ Async ThreadPoolTaskExecutor & Java 21 Virtual Threads (ThreadPoolTaskExecutor, CallerRunsPolicy, CompletableFuture, Virtual Threads)
---

# Chapter 290: Async Processing & Thread Pools — @Async, CompletableFuture, ThreadPoolTaskExecutor, Backpressure, Virtual Threads

## 1. Concept

In high-throughput microservice architectures like FinFlow, incoming HTTP requests are handled by a dedicated web server thread pool (e.g. Tomcat `http-nio` threads). If a request performs long-running computations (e.g. generating a 50-page monthly statement PDF, heavy cryptography, or multi-gateway aggregation), holding the web container thread blocks new incoming connections, rapidly causing **thread pool starvation** and HTTP 503/504 errors.

Spring Boot provides asynchronous execution via `@EnableAsync`, `@Async`, `CompletableFuture<T>`, and `ThreadPoolTaskExecutor`. 

Additionally, **Java 21 Virtual Threads (Project Loom)** introduce lightweight, user-mode threads capable of scaling I/O-bound tasks to hundreds of thousands of concurrent operations without consuming OS platform threads.

```
+-------------------------------------------------------------------------------------------------+
|                                 The Golden Rules of Production Async                            |
|                                                                                                 |
|  1. Never Use Default @Async without an Explicit Executor: Spring Boot defaults to             |
|     SimpleAsyncTaskExecutor, which spawns an UNPOOLED native OS thread per task -> leading     |
|     directly to "OutOfMemoryError: unable to create native thread" under load.                 |
|  2. Never Use Unbounded Queues: An unbounded LinkedBlockingQueue ignores maxPoolSize and        |
|     accumulates tasks until JVM heap memory is exhausted.                                      |
|  3. Always Configure CallerRunsPolicy for Backpressure: When the queue fills, CallerRunsPolicy |
|     forces the submitting thread to execute the task, slowing down ingress naturally.           |
|  4. Always Handle Async Uncaught Exceptions: Exceptions in @Async void methods are silently     |
|     swallowed unless an AsyncUncaughtExceptionHandler is registered.                            |
|  5. Propagate Context with TaskDecorator: ThreadLocal state (SecurityContext, MDC trace IDs)   |
|     is lost across async boundaries unless explicitly copied via a TaskDecorator.               |
+-------------------------------------------------------------------------------------------------+
```

---

## 2. Internal Working

### Spring `@Async` Proxy Mechanics

```
Caller ──► Spring AOP Proxy (CGLIB)
                 │
                 ▼
     AnnotationAsyncExecutionInterceptor
                 │
                 ├── 1. Looks up Executor specified in @Async("executorName")
                 │
                 ├── 2. Wraps task with TaskDecorator (MDC / Security Context copy)
                 │
                 ├── 3. Submits task to ThreadPoolTaskExecutor
                 │
                 └── 4. Returns CompletableFuture immediately to caller!
```

---

### ThreadPoolExecutor Sizing & Queuing Algorithm

A common misconception is that a `ThreadPoolExecutor` increases threads from `corePoolSize` to `maxPoolSize` as soon as tasks arrive. In reality, the JVM executes the following strict 4-step sequence:

```
Task Submitted
      │
      ├── 1. activeThreads < corePoolSize?
      │      ├── YES ──► Spawn new Core Worker Thread immediately!
      │      └── NO
      │
      ├── 2. Attempt to add to Work Queue (queueCapacity)
      │      ├── Queue has space ──► Task placed in Queue (Core threads consume from queue)
      │      └── Queue is FULL!
      │
      ├── 3. activeThreads < maxPoolSize?
      │      ├── YES ──► Spawn new Non-Core Worker Thread!
      │      └── NO (Pool is at max capacity AND Queue is FULL!)
      │
      └── 4. Trigger RejectedExecutionHandler!
```

$$\text{Total Task Capacity before Rejection} = \text{maxPoolSize} + \text{queueCapacity}$$

> [!WARNING]
> **The Unbounded Queue Trap**: If you use an unbounded `LinkedBlockingQueue` (default `queueCapacity = Integer.MAX_VALUE`), Step 2 *never fails*. As a result, Step 3 is **never reached**, `maxPoolSize` is completely ignored, and the pool will never scale beyond `corePoolSize`!

---

### Rejection Policies Comparison

| Rejection Policy | Behavior when Queue & Max Pool are Saturated | Production Suitability |
|---|---|---|
| **`AbortPolicy`** *(Default)* | Throws `RejectedExecutionException`. | Good for failing fast when upstream callers catch the exception. |
| **`CallerRunsPolicy`** *(Recommended)* | The thread that submitted the task executes it directly in-line. | **Optimal**: Provides natural backpressure by slowing down the caller. |
| **`DiscardPolicy`** | Silently drops the task without throwing an exception. | **Dangerous**: Causes silent data loss in financial systems. |
| **`DiscardOldestPolicy`** | Drops the oldest unhandled task in the queue and retries submission. | **Dangerous**: Drops unpredictable past work. |

---

### ThreadLocal & Context Propagation (`TaskDecorator`)

Because `@Async` switches execution to a worker thread from the pool, standard `ThreadLocal` variables (e.g. SLF4J `MDC`, Spring Security `SecurityContextHolder`, `RequestContextHolder`) do **not** cross the thread boundary:

```java
public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap); // Propagate trace_id to async thread!
                }
                runnable.run();
            } finally {
                MDC.clear(); // Clean up thread pool worker
            }
        };
    }
}
```

---

### Java 21 Virtual Threads (Project Loom)

Java 21 introduces **Virtual Threads** (`java.lang.Thread.ofVirtual()`):

```
┌─────────────────────────────────────────────────────────────┐
│                      Java 21 Virtual Threads                │
│                                                             │
│  Virtual Thread 1 ──┐                                       │
│  Virtual Thread 2 ──┼──► Carrier Thread 1 (OS Thread)       │
│  Virtual Thread 3 ──┘                                       │
│                                                             │
│  Virtual Thread 4 ──┐                                       │
│  Virtual Thread 5 ──┼──► Carrier Thread 2 (OS Thread)       │
│  Virtual Thread 6 ──┘                                       │
└─────────────────────────────────────────────────────────────┘
```

- **How it works**: When a virtual thread executes a blocking I/O operation (e.g. database query, HTTP call, `Thread.sleep`), the JVM **unmounts** the virtual thread from its underlying OS Carrier Thread. The carrier thread immediately executes other virtual threads. When I/O completes, the virtual thread is remounted on an available carrier thread.
- **Platform Thread Pool vs Virtual Threads**:
  - Use **Virtual Threads** for I/O-bound operations (network calls, external API aggregation).
  - Use **Platform Thread Pools (`ThreadPoolTaskExecutor`)** for CPU-bound operations (image rendering, PDF generation, hashing) where virtual threads provide no throughput benefit and pooling is needed to cap CPU saturation.

---

## 3. Enterprise Scenario: FinFlow Merchant Monthly Statement Generation

In the **FinFlow Reference Architecture**:

```
Month-End Ingress (250,000 merchants) ──► Payment Service (20 pods)
                                               │
                                               ▼ (@Async("statementExportExecutor"))
                                 ThreadPoolTaskExecutor (stmt-export-)
                                       ├── Core: 4, Max: 8, Queue: 10
                                       ├── Bounded Capacity: 18 tasks / pod
                                       └── Rejection: CallerRunsPolicy (Natural Backpressure)
                                               │
                                               ▼
                                 Statement PDF / CSV Rendering & S3 Upload
```

- **SLA**: Render monthly statements asynchronously in $< 2\text{ seconds}$ without impacting live checkout APIs.
- **Backpressure**: When all 20 pods are saturated, `CallerRunsPolicy` slows down the batch submission process without dropping export jobs.

---

## 4. Incorrect Implementation

Below is a vulnerable async implementation typical of unhardened Spring Boot systems:

```java
package com.finflow.chapter290.incorrect;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Default SimpleAsyncTaskExecutor creates a new native OS thread per task -> OutOfMemoryError.
 * 2. @Async void methods drop exceptions silently without logging or alerting.
 */
@Service
public class StatementExportServiceIncorrect {

    private final AtomicInteger spawnedThreadCount = new AtomicInteger(0);

    /**
     * Anti-Pattern: Unpooled thread creation under heavy traffic.
     */
    public void executeUnboundedAsync() {
        // Simulates unpooled thread creation
        spawnedThreadCount.incrementAndGet();
    }
}
```

---

## 5. Production Incident

### Incident Timeline

| Time (UTC) | Event / Telemetry |
|---|---|
| **00:00:00** | Month-end batch job triggers statement generation for 250,000 merchants across 4 Kubernetes pods. |
| **00:00:05** | The endpoint was annotated with `@Async` without specifying an explicit `TaskExecutor` bean, defaulting to Spring's `SimpleAsyncTaskExecutor`. |
| **00:00:15** | `SimpleAsyncTaskExecutor` attempts to spawn 18,000 unpooled Linux platform threads simultaneously across the 4 pods. |
| **00:00:30** | Linux kernel reaches the container's PID limit: `cgroup: fork rejected by pids controller in /kubepods/burstable/...`. |
| **00:00:45** | The JVM crashes with `java.lang.OutOfMemoryError: unable to create native thread`. |
| **00:01:00** | Kubernetes marks all 4 pods `CrashLoopBackOff`. Live payment traffic hitting the same pods fails with HTTP 502 Bad Gateway. |
| **00:02:00** | PagerDuty SEV-0 fired: **$18.4M** in month-end merchant billing stalled. |
| **00:25:00** | SREs deploy hotfix: Defined a bounded `ThreadPoolTaskExecutor` (core=8, max=16, queue=100) with `CallerRunsPolicy` and graceful shutdown. |
| **00:35:00** | Pods stabilize at $< 150$ total platform threads; 250,000 statements process cleanly in 18 minutes with zero impact on live checkouts. |

---

## 6. Logs & Diagnostics

### 1. Native Thread Exhaustion Crash Log
```text
2026-08-20T00:00:45.112Z ERROR [statement-service] 1 --- [http-nio-8080-exec-42] o.a.c.c.C.[.[.[/].[dispatcherServlet]    : Servlet.service() for servlet [dispatcherServlet] in context with path [] threw exception

java.lang.OutOfMemoryError: unable to create native thread
	at java.base/java.lang.Thread.start0(Native Method)
	at java.base/java.lang.Thread.start(Thread.java:1535)
	at org.springframework.core.task.SimpleAsyncTaskExecutor.doExecute(SimpleAsyncTaskExecutor.java:240)
	at org.springframework.core.task.SimpleAsyncTaskExecutor.execute(SimpleAsyncTaskExecutor.java:214)
	at org.springframework.aop.interceptor.AsyncExecutionInterceptor.invoke(AsyncExecutionInterceptor.java:115)
```

### 2. Async Uncaught Exception Handler Log
```text
2026-08-20T00:15:10.884Z ERROR [statement-service,trace_id=4a5b6c] 1 --- [stmt-export-3] c.f.c.c.AsyncConfig$CustomAsyncExceptionHandler : Async error in method: fireAndForgetNotification with message: Simulated failure in fire-and-forget async method
```

---

## 7. Root Cause Analysis

```
+-------------------------------------------------------------------------------------------------+
|                               Async Outage Root Cause Chain                                     |
|                                                                                                 |
|  1. Unconfigured @Async (SimpleAsyncTaskExecutor)                                               |
|     └── Created a brand new OS platform thread for every statement export request.              |
|                                                                                                 |
|  2. Native Thread & PID Exhaustion                                                              |
|     └── 18,000 OS threads exhausted Linux kernel cgroup PID limits, causing JVM crash.          |
|                                                                                                 |
|  3. Cascading Pod Failure & Payment Ingress Outage                                              |
|     └── Pod crashes took down co-located payment checkout APIs.                                 |
|                                                                                                 |
|  4. Remediation: Bounded ThreadPoolTaskExecutor + CallerRunsPolicy + Virtual Threads            |
|     └── Capped native OS threads at 16; CallerRunsPolicy provided natural ingress backpressure. |
+-------------------------------------------------------------------------------------------------+
```

---

## 8. Debugging Process

```
[1. Thread Count Inspection] Run: jcmd <pid> Thread.print | grep "tid=" | wc -l
       │
[2. Monitor Executor Metrics] Check Prometheus metrics: executor_active, executor_queue_remaining
       │
[3. Inspect Rejections] Check Prometheus metric executor_rejected_total
       │
[4. Verify MDC Propagation] Confirm async worker thread logs retain trace_id from HTTP request
       │
[5. Rollout] Enforce bounded ThreadPoolTaskExecutor and verify CallerRuns under stress test
```

---

## 9. Correct Implementation

### 1. Production Async Configuration: `AsyncConfig.java`

```java
package com.finflow.chapter290.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    private final AtomicInteger uncaughtExceptionCount = new AtomicInteger(0);

    @Bean(name = "statementExportExecutor")
    public Executor statementExportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(10);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("stmt-export-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("virtual-stmt-");
        executor.setVirtualThreads(true); // Java 21 Project Loom Virtual Threads
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler(uncaughtExceptionCount);
    }

    public int getUncaughtExceptionCount() { return uncaughtExceptionCount.get(); }
    public void resetUncaughtExceptionCount() { uncaughtExceptionCount.set(0); }

    public static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        private final AtomicInteger counter;

        public CustomAsyncExceptionHandler(AtomicInteger counter) {
            this.counter = counter;
        }

        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            counter.incrementAndGet();
            log.error("Async error in method: {} with message: {}", method.getName(), ex.getMessage(), ex);
        }
    }
}
```

---

### 2. Hardened Asynchronous Service: `StatementExportServiceCorrect.java`

```java
package com.finflow.chapter290.correct;

import com.finflow.chapter290.domain.StatementExportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Service
public class StatementExportServiceCorrect {

    private static final Logger log = LoggerFactory.getLogger(StatementExportServiceCorrect.class);

    @Async("statementExportExecutor")
    public CompletableFuture<StatementExportRequest> generateStatementAsync(StatementExportRequest request) {
        log.info("Processing export on platform thread: {}", Thread.currentThread().getName());

        try {
            Thread.sleep(50); // Simulate rendering PDF
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        request.setStatus("COMPLETED");
        request.setGeneratedFileUrl("https://s3.finflow.io/statements/" + request.getRequestId() + ".pdf");
        request.setCompletedAt(Instant.now());

        return CompletableFuture.completedFuture(request);
    }

    @Async("virtualThreadExecutor")
    public CompletableFuture<Boolean> fetchReportOnVirtualThread() {
        boolean isVirtual = Thread.currentThread().isVirtual();
        log.info("Executing on Java 21 Virtual Thread? isVirtual={}, thread={}", isVirtual, Thread.currentThread());
        return CompletableFuture.completedFuture(isVirtual);
    }

    @Async
    public void fireAndForgetNotification(String merchantId, boolean fail) {
        log.info("Sending fire-and-forget notification to merchant: {}", merchantId);
        if (fail) {
            throw new IllegalStateException("Simulated failure in fire-and-forget async method for merchant: " + merchantId);
        }
    }
}
```

---

## 10. Performance Comparison

Benchmarked on 10,000 asynchronous tasks under high concurrency.

| Metric | Unconfigured @Async (`SimpleAsyncTaskExecutor`) | Bounded `ThreadPoolTaskExecutor` (CallerRuns) | Java 21 Virtual Threads (`setVirtualThreads(true)`) |
|---|---|---|---|
| **OS Thread Allocation** | 10,000 threads *(Crash / OOM)* | **16 threads (Bounded & pooled)** | **12 Carrier threads (OS-level)** |
| **Heap Memory Consumed** | $> 10\text{ GB}$ *(1MB/thread stack)* | **$\approx 35\text{ MB}$** | **$\approx 4\text{ MB}$ ($\approx 200\text{B}$/virtual thread)** |
| **Overload Behavior** | JVM Crash (`OutOfMemoryError`) | **CallerRuns Natural Backpressure** | **High Concurrency Non-blocking I/O** |
| **Task Rejection Rate** | 0% (before crash) | **0% (Executed by caller thread)** | **0% (Scheduled on carrier pool)** |
| **Exception Observability**| Swallowed Silently | **100% (Logged & Counted by Handler)**| **100% (Logged by Handler)** |

---

## 11. Best Practices

### The Do's
- **DO name and configure custom `ThreadPoolTaskExecutor` beans**: Never rely on unconfigured `@Async`.
- **DO use `CallerRunsPolicy` on critical asynchronous workloads**: Guarantees zero tasks are lost when the pool is saturated while providing backpressure.
- **DO return `CompletableFuture<T>` from `@Async` methods**: Enables non-blocking chaining, timeouts, and structured concurrency.
- **DO configure `setWaitForTasksToCompleteOnShutdown(true)`**: Ensures in-flight async tasks finish before the JVM terminates.
- **DO use Java 21 Virtual Threads for high-concurrency I/O**: Eliminates thread pool tuning for network-bound tasks.

### The Don'ts
- **DON'T call `@Async` methods from within the same class (Self-Invocation)**: Bypasses the Spring CGLIB proxy; executes synchronously on the calling thread.
- **DON'T use `@Async void` without an `AsyncUncaughtExceptionHandler`**: Unchecked exceptions are lost into the void.
- **DON'T configure unbounded queues (`queueCapacity = Integer.MAX_VALUE`)**: Causes memory bloat and prevents `maxPoolSize` from ever activating.
- **DON'T use `synchronized` blocks in Java 21 Virtual Threads**: Pins the virtual thread to its OS carrier thread; use `java.util.concurrent.locks.ReentrantLock` instead.

---

## 12. Common Mistakes

### Mistake 1: Self-Invocation Proxy Bypass
```java
@Service
public class ReportService {
    public void generateAll() {
        this.generateSingleReportAsync(); // BUG: Synchronous direct method call!
    }

    @Async
    public void generateSingleReportAsync() { ... }
}
```
**Why it fails**: Calling the method via `this` invokes the raw instance directly, bypassing the Spring AOP interceptor.
**Production Fix**: Inject the bean into itself or move the `@Async` method to a separate service component.

### Mistake 2: The Unbounded Queue `maxPoolSize` Illusion
Setting `corePoolSize = 5`, `maxPoolSize = 50`, `queueCapacity = Integer.MAX_VALUE`.
**Why it fails**: Because the queue never fills, the pool **never scales above 5 threads**, regardless of load!
**Production Fix**: Set a realistic, bounded `queueCapacity` (e.g. 50–200).

---

## 13. Interview Questions

### Junior Tier
**Q: How does Spring's `@Async` annotation work under the hood?**
> **Answer**: During application startup, `AsyncAnnotationBeanPostProcessor` detects classes or methods annotated with `@Async` and wraps them in a Spring AOP proxy (via CGLIB or JDK dynamic proxy). When the method is invoked, `AnnotationAsyncExecutionInterceptor` intercepts the call, captures the task, submits it to a configured `TaskExecutor` thread pool, and returns immediately to the caller (returning `null` for `void` or a `CompletableFuture` for asynchronous results).

### Mid Tier
**Q: Explain the exact step-by-step lifecycle of a `ThreadPoolExecutor` when submitting a task.**
> **Answer**: 
> 1. If active worker threads $< \text{corePoolSize}$, a new core thread is spawned immediately to execute the task.
> 2. If active worker threads $\ge \text{corePoolSize}$, the executor attempts to place the task into the internal work queue (`queueCapacity`).
> 3. If the queue is full AND active worker threads $< \text{maxPoolSize}$, a new non-core worker thread is spawned.
> 4. If the queue is full AND active worker threads $\ge \text{maxPoolSize}$, the executor invokes the configured `RejectedExecutionHandler`.

### Senior Tier
**Q: Why are `ThreadLocal` variables lost in `@Async` methods, and how does `TaskDecorator` fix this?**
> **Answer**: `ThreadLocal` variables are bound to the specific native thread executing the request. When an `@Async` method is invoked, execution switches to a worker thread from the thread pool, which has an entirely empty `ThreadLocalMap`. This causes loss of logging correlation IDs (`MDC`), Security Contexts (`SecurityContextHolder`), and tenant contexts. `TaskDecorator` provides a callback executed on the *submitting* thread right before task submission, allowing developers to clone the current thread's context map and re-apply it inside a `try-finally` block on the worker thread when the task runs.

### Staff Tier
**Q: Compare Java 21 Virtual Threads with Platform Thread Pools. What is the Carrier Thread Pinning hazard?**
> **Answer**: 
> - **Platform Threads**: 1:1 mapped to OS kernel threads, heavy memory footprint ($\sim 1\text{ MB}$ stack), expensive context switching. Sized with bounded pools.
> - **Virtual Threads**: Lightweight user-mode threads managed by the JVM, tiny memory footprint ($\sim 200\text{ bytes}$), unmounted from carrier OS threads during blocking socket I/O.
> - **Carrier Thread Pinning Hazard**: If a virtual thread enters a `synchronized` block/method or invokes native JNI code and then executes blocking I/O, the JVM **cannot unmount** the virtual thread from its underlying OS carrier thread. If many virtual threads are pinned simultaneously, the carrier pool is exhausted, causing severe latency spikes across all virtual threads. The solution is replacing `synchronized` with `ReentrantLock`.

### Principal Tier
**Q: Design a Fault-Tolerant, Multi-Stage Asynchronous Pipeline for high-value financial checkout orchestrations with Bulkhead Isolation.**
> **Answer**: A Principal-level architecture uses **Structured Concurrency with Bulkhead Thread Isolation**:
> 1. **Bulkhead Isolation**: Assign separate, dedicated `ThreadPoolTaskExecutor` instances for each external dependency (e.g. `fraudScoreExecutor`, `taxCalculationExecutor`, `fxRateExecutor`). A failure or slow down in Fraud Scoring never starves Tax Calculation.
> 2. **Structured Non-Blocking Chaining**: Use `CompletableFuture.allOf()` combined with `.orTimeout(500, TimeUnit.MILLISECONDS)`:
>    ```java
>    CompletableFuture<FraudResult> fraudFuture = CompletableFuture.supplyAsync(this::checkFraud, fraudExecutor).orTimeout(500, TimeUnit.MILLISECONDS).exceptionally(this::fraudFallback);
>    CompletableFuture<TaxResult> taxFuture = CompletableFuture.supplyAsync(this::computeTax, taxExecutor).orTimeout(300, TimeUnit.MILLISECONDS);
>    CompletableFuture.allOf(fraudFuture, taxFuture).thenApply(v -> combine(fraudFuture.join(), taxFuture.join()));
>    ```
> 3. **Graceful Degradation**: Fallback paths provide deterministic default responses on timeout without blocking the main checkout response.

---

## 14. Hands-on Exercise

### Objective
Implement an MDC `TaskDecorator` that transfers logging trace IDs from web threads to async threads:
1. Capture `MDC.getCopyOfContextMap()` on the submitting thread.
2. Apply context on the worker thread.
3. Clean up `MDC.clear()` in a `finally` block.

### Solution

```java
public class MdcContextTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) {
                    MDC.setContextMap(contextMap);
                }
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
```

---

## 15. Advanced Challenge: Bulkhead Isolated Async Aggregator

### Enterprise Problem Statement
Build an asynchronous payment enrichment service that fans out to Fraud, Forex, and Ledger services concurrently across isolated bulkhead thread pools with strict timeouts and fallbacks.

### Enterprise Solution

```java
@Service
public class PaymentEnrichmentAggregator {

    private final Executor fraudExecutor;
    private final Executor forexExecutor;

    public PaymentEnrichmentAggregator(
            @Qualifier("fraudExecutor") Executor fraudExecutor,
            @Qualifier("forexExecutor") Executor forexExecutor) {
        this.fraudExecutor = fraudExecutor;
        this.forexExecutor = forexExecutor;
    }

    public CompletableFuture<EnrichedPayment> enrichPayment(Payment payment) {
        CompletableFuture<String> fraudCheck = CompletableFuture.supplyAsync(() -> "PASS", fraudExecutor)
                .orTimeout(300, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> "FRAUD_TIMEOUT_FALLBACK");

        CompletableFuture<BigDecimal> fxRate = CompletableFuture.supplyAsync(() -> BigDecimal.valueOf(1.08), forexExecutor)
                .orTimeout(200, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> BigDecimal.ONE);

        return CompletableFuture.allOf(fraudCheck, fxRate)
                .thenApply(v -> new EnrichedPayment(payment.getId(), fraudCheck.join(), fxRate.join()));
    }
}
```

---

## 16. Production Checklist

Before approving any pull request involving `@Async` and thread pools:

- [ ] **Custom `TaskExecutor` Declared**: Verify `@Async` specifies a named executor bean.
- [ ] **Bounded Queue Capacity**: Ensure `queueCapacity` is bounded (e.g. 50–200).
- [ ] **`CallerRunsPolicy` Configured**: Confirm `setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy())` is set.
- [ ] **`AsyncUncaughtExceptionHandler` Registered**: Verify exceptions in `void` async methods are captured and logged.
- [ ] **Context Propagation via `TaskDecorator`**: Confirm MDC trace IDs and SecurityContext are decorated across thread boundaries.
- [ ] **No Self-Invocation**: Confirm `@Async` methods are called across bean boundaries.
- [ ] **Graceful Shutdown Configured**: Verify `setWaitForTasksToCompleteOnShutdown(true)` is enabled.
