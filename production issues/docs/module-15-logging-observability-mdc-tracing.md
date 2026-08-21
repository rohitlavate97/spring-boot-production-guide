# Module 15: Logging, Observability, MDC & Tracing Gaps

## Issue 15.1: Asynchronous MDC Context Loss, Distributed Tracing Gaps, and Synchronous Appender Contention

---

### 1. Scenario

During a production investigation on the **FinFlow Core Banking & Settlement Pipeline**:
1. A high-priority $1.2M international wire transfer failed during background batch processing.
2. When SREs queried centralized logs (Datadog/Splunk/Elastic) using the user's `X-Correlation-ID`, only the initial HTTP controller logs appeared. All background execution logs inside `@Async` worker threads printed `[corrId=NONE, traceId=NONE]`, **severing the audit trail and blinding the incident response team**.
3. Concurrently, during a 10,000 req/sec load spike, API latency skyrocketed from 15ms to 3,500ms. Thread dumps revealed that **180 out of 200 Tomcat worker threads were BLOCKED** contending for the internal synchronization lock of a synchronous Logback `ConsoleAppender` (`OutputStreamAppender.subAppend()`).
4. To mitigate the contention, an engineer switched to Logback's `AsyncAppender`, but left default settings. During the next traffic burst, **25% of all production `INFO` audit logs were silently discarded** due to the default `discardingThreshold: 20` rule!

---

### 2. Symptoms

```text
1. Disconnected Distributed Traces:
   Logs emitted from @Async methods, CompletableFuture workers, or Scheduled tasks show [corrId=NONE, traceId=NONE].
2. Severe Thread Synchronization Contention:
   Thread dumps showing dozens of worker threads in BLOCKED state waiting for org.apache.catalina.connector.CoyoteOutputStream or ch.qos.logback.core.OutputStreamAppender.
3. Silent Log Dropping:
   Critical INFO-level business and audit events missing from log storage during high load periods without any error or exception thrown.
4. Cross-Request Correlation Leaks:
   A thread pool worker logs Request B's operations with Request A's correlationId because MDC.clear() was omitted in a previous execution.
5. High Garbage Collection Pressure:
   High allocation rate caused by eager string concatenation inside disabled DEBUG/TRACE log statements (e.g. logger.debug("User " + user + " data " + payload)).
```

---

### 3. Possible Root Causes

1. **ThreadLocal Nature of SLF4J MDC:** `MDC` is backed by `ThreadLocal<Map<String, String>>`. Asynchronous workers run on separate threads with empty context maps unless explicitly propagated via Spring's `TaskDecorator`.
2. **Synchronous Logging Lock Contention:** Standard console and file appenders acquire a synchronized monitor lock per log event, turning a multi-threaded web server into a single-threaded bottleneck during heavy I/O.
3. **Logback `discardingThreshold` Default:** By default, Logback's `AsyncAppender` silently drops `TRACE`, `DEBUG`, and `INFO` events when its buffer queue capacity drops below 20%.
4. **Missing MDC Cleanup in Servlet Filters:** Omitting `MDC.remove()` or `MDC.clear()` in a `finally` block leaves stale correlation IDs on pooled threads.

---

### 4. Architecture Context: MDC Propagation Pipeline via TaskDecorator

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        DISTRIBUTED MDC & TRACING PROPAGATION                           │
│                                                                                        │
│  [HTTP Request with X-Correlation-ID]                                                  │
│           │                                                                            │
│           ▼                                                                            │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │ CorrelationIdFilter (Servlet Filter)                                             │  │
│  │ 1. Extract X-Correlation-ID (or generate UUID)                                   │  │
│  │ 2. MDC.put("correlationId", corrId)                                              │  │
│  │ 3. response.setHeader("X-Correlation-ID", corrId)                                │  │
│  └────────────────────────────────────────┬─────────────────────────────────────────┘  │
│                                           │                                            │
│                                           ▼                                            │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │ Web Controller / Service Thread (Main Request Thread)                            │  │
│  │ [MDC contains: correlationId=CORR-1234, traceId=6a88a927...]                     │  │
│  │                                                                                  │  │
│  │ Dispatches task to @Async("observabilityTaskExecutor")                           │  │
│  └────────────────────────────────────────┬─────────────────────────────────────────┘  │
│                                           │                                            │
│                                           ▼                                            │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │ MdcTaskDecorator (Spring TaskDecorator)                                          │  │
│  │ 1. Map<String, String> context = MDC.getCopyOfContextMap() [ON CALLER THREAD]   │  │
│  │ 2. Spawns Runnable:                                                              │  │
│  │      MDC.setContextMap(context); [ON WORKER THREAD]                              │  │
│  │      try { task.run(); }                                                         │  │
│  │      finally { MDC.clear(); }                                                    │  │
│  └────────────────────────────────────────┬─────────────────────────────────────────┘  │
│                                           │                                            │
│                                           ▼                                            │
│  ┌──────────────────────────────────────────────────────────────────────────────────┐  │
│  │ Async Worker Thread (obs-async-1)                                                │  │
│  │ Logs inherit [corrId=CORR-1234, traceId=6a88a927...] seamlessly!                 │  │
│  └──────────────────────────────────────────────────────────────────────────────────┘  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### Step 1: Submit `@Async` Task Without `TaskDecorator`
```java
// ❌ ANTI-PATTERN: MDC is lost when task switches to worker thread
@Async
public void processAsync(String orderId) {
    // MDC.get("correlationId") returns NULL!
    log.info("Processing order {}", orderId); // Logs: [corrId=NONE]
}
```

#### Step 2: Use Synchronous Appenders Under High Load
```xml
<!-- ❌ ANTI-PATTERN: Synchronous ConsoleAppender serializes all threads on System.out -->
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
        <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
</appender>
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Capture Thread Dump During Logging Lock Contention
```bash
jstack <PID> | grep -A 10 "ch.qos.logback.core.OutputStreamAppender"
```
**Diagnostic Output:**
```text
"http-nio-8080-exec-42" #78 daemon prio=5 tid=0x00007f... nid=0x3c4d waiting for monitor entry
   java.lang.Thread.State: BLOCKED (on object monitor)
	at ch.qos.logback.core.OutputStreamAppender.subAppend(OutputStreamAppender.java:231)
	- waiting to lock <0x0000000702468130> (a ch.qos.logback.core.ConsoleAppender)
	at ch.qos.logback.core.OutputStreamAppender.append(OutputStreamAppender.java:102)
	at ch.qos.logback.classic.spi.LoggingEvent.writeTo(LoggingEvent.java:120)
```

#### Method 2: Inspect Logback AsyncAppender Status Listener
Add status listener in `logback-spring.xml` to detect dropped events:
```xml
<statusListener class="ch.qos.logback.core.status.OnConsoleStatusListener" />
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Inspect Ingestion Logs for Missing MDC Fields.
        Search Datadog/Splunk for logs with empty or NONE correlation IDs.

Step 2: Implement MdcTaskDecorator for Spring Thread Pools.
        Implement org.springframework.core.task.TaskDecorator to capture MDC.getCopyOfContextMap()
        and set it inside worker threads.

Step 3: Attach TaskDecorator to ThreadPoolTaskExecutor.
        Configure executor.setTaskDecorator(new MdcTaskDecorator()).

Step 4: Configure Non-Blocking AsyncAppender with Zero Discarding.
        Wrap file/console appenders with ch.qos.logback.classic.AsyncAppender, setting
        discardingThreshold to 0 (never drop INFO/WARN/ERROR logs) and queueSize to 1024.

Step 5: Enforce Parameterized Logging.
        Replace string concatenation with parameterized placeholders (logger.info("Order: {}", id))
        to eliminate unnecessary StringBuilder allocations.
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. Why MDC Fails Across Thread Pools
- `MDC` is implemented via SLF4J's `MDCAdapter` (in Logback, `LogbackMDCAdapter`), which delegates to an internal `ThreadLocal<Map<String, String>>`.
- When an HTTP thread receives a request, `CorrelationIdFilter` sets the thread's local map.
- When `orderService.processAsync()` is called, Spring's `@Async` interceptor submits a `Runnable` to a background thread pool (`ThreadPoolTaskExecutor`).
- The worker thread that picks up the task has its **own separate `ThreadLocal` map**, which is initialized to `null`.
- Without a `TaskDecorator` to copy the map across thread boundaries, the correlation context is lost.

#### 2. The `discardingThreshold` Formula in Logback `AsyncAppender`
```java
// Logback AsyncAppenderBase.java
protected boolean isQueueBelowDiscardingThreshold() {
    return (parent.getRemainingCapacity() < discardingThreshold);
}
```
If `queueSize = 500` and `discardingThreshold = 20` (default: 20% = 100 slots remaining), once 400 events queue up, **all events with level $\le$ INFO are discarded silently!** Setting `discardingThreshold = 0` retains all events.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Servlet `CorrelationIdFilter`
```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
    public static final String CORRELATION_ID_MDC_KEY = "correlationId";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = request.getHeader(CORRELATION_ID_HEADER);
        if (!StringUtils.hasText(correlationId)) {
            correlationId = UUID.randomUUID().toString();
        }

        MDC.put(CORRELATION_ID_MDC_KEY, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_MDC_KEY); // Prevent pool pollution!
        }
    }
}
```

#### ✅ Fix 2: `MdcTaskDecorator` & Thread Pool Configuration
```java
public class MdcTaskDecorator implements TaskDecorator {
    @Override
    public Runnable decorate(Runnable runnable) {
        Map<String, String> contextMap = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (contextMap != null) MDC.setContextMap(contextMap);
                else MDC.clear();
                runnable.run();
            } finally {
                MDC.clear();
            }
        };
    }
}

@Configuration
public class ObservabilityThreadPoolConfig {

    @Bean(name = "observabilityTaskExecutor")
    public ThreadPoolTaskExecutor observabilityTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(16);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("obs-async-");
        executor.setTaskDecorator(new MdcTaskDecorator()); // Attaches MDC decorator
        executor.initialize();
        return executor;
    }
}
```

#### ✅ Fix 3: Production `logback-spring.xml` Configuration
```xml
<configuration>
    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level [corrId=%X{correlationId:-NONE}, traceId=%X{traceId:-NONE}] %logger{36} - %msg%n</pattern>
        </encoder>
    </appender>

    <!-- Production Non-Blocking AsyncAppender -->
    <appender name="ASYNC_CONSOLE" class="ch.qos.logback.classic.AsyncAppender">
        <appender-ref ref="CONSOLE" />
        <queueSize>1024</queueSize>
        <discardingThreshold>0</discardingThreshold> <!-- Never drop INFO logs! -->
        <neverBlock>false</neverBlock>
        <maxFlushTime>1000</maxFlushTime>
    </appender>

    <root level="INFO">
        <appender-ref ref="ASYNC_CONSOLE" />
    </root>
</configuration>
```

---

### 10. Verification

1. **Correlation Filter Test:** Run `CorrelationIdFilterTest.java` to verify that `X-Correlation-ID` is extracted or auto-generated and returned in response headers.
2. **Async MDC Propagation Test:** Run `MdcPropagationAsyncTest.java` to verify that MDC context maps propagate seamlessly into `@Async` worker threads.
3. **Integration Test:** Run `Module15IntegrationTest.java` to verify end-to-end trace correlation from HTTP request to async service execution.

---

### 11. Prevention & Production Readiness

1. **Enforce `TaskDecorator` in Custom Thread Pools:**
   Use ArchUnit rules to ensure all `ThreadPoolTaskExecutor` beans define a `TaskDecorator`.
2. **Always Configure `discardingThreshold = 0` on `AsyncAppender`:**
   Prevent silent loss of critical audit logs in production environments.
3. **Standardize on W3C `traceparent` Headers:**
   Ensure API gateways and downstream clients propagate W3C TraceContext headers for distributed trace continuity.

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: How does SLF4J MDC work internally, and why does it fail across asynchronous thread boundaries?**
2. **Q: What is Spring's `TaskDecorator` interface, and how is it used to solve MDC loss?**
3. **Q: Why does synchronous logging cause thread contention under high concurrency?**
4. **Q: What is the risk of leaving the default `discardingThreshold` in Logback's `AsyncAppender`?**
5. **Q: What is the structure of the W3C `traceparent` header, and how does Micrometer Tracing parse it?**

#### Production Incident Questions
1. **Incident:** A production microservice experiences 100% CPU usage and latency spikes. Thread dumps show 150 threads in `BLOCKED` state on `ConsoleAppender`. How do you fix this immediately?
2. **Incident:** You search Splunk for a customer's transaction ID. Half of the logs are missing. You discover Logback's `AsyncAppender` queue filled up during a traffic surge. What happened to the missing logs?
3. **Incident:** An async payment processing worker logged User B's credit card token under User A's correlation ID. How did a missing `MDC.clear()` cause this audit disaster?
4. **Incident:** A developer wrote `log.debug("Found entity: " + jsonMapper.writeValueAsString(entity))` inside a tight loop. Even with logging level set to `INFO`, performance degraded severely. Why?
5. **Incident:** How do you configure OpenTelemetry / Micrometer Tracing to sample only 5% of normal requests but 100% of errors?

#### Trick Questions
1. **Trick:** If an exception is logged with `log.error("Failed to process order", ex)`, does SLF4J require a `{}` placeholder for the `Throwable` argument? *(Hint: No, Throwable is always passed as the last argument!)*
2. **Trick:** Does `MDC.put()` modify the context map for child threads spawned via `new Thread().start()`?
3. **Trick:** What happens if `AsyncAppender` has `neverBlock = true` and the queue fills up?

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
