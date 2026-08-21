---
chapter: 330
topic: OpenTelemetry & Distributed Tracing — Auto-instrumentation, Span Propagation, W3C Trace Context, Trace-Log Correlation, Baggage, Sampling Strategies
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270, 280, 290, 300, 310, 320]
reference_system_node: Payment Service Distributed Tracing Engine ↔ Micrometer Tracing & OpenTelemetry Bridge (Tracer, Span, ScopedSpan, W3C Traceparent, Tracestate, Baggage, MDC Correlation, Context Propagation, Tail-Based Sampling)
---

# Chapter 330: OpenTelemetry & Distributed Tracing — Auto-instrumentation, Span Propagation, W3C Trace Context, Trace-Log Correlation, Baggage, Sampling Strategies

## 1. Concept

In modern distributed microservice architectures, a single user transaction often traverses 10 to 30 distinct services (API Gateways, Payment Routers, Fraud Detection Engines, Database Ledgers, and Third-Party Banking Gateways).

While **Metrics** tell you *that* latency spiked and **Logs** record *what* happened inside an individual method, only **Distributed Tracing** provides the causal graph showing *where* time was spent and *how* requests flowed across network boundaries.

Spring Boot integrates with **Micrometer Tracing** (formerly Spring Cloud Sleuth) backed by the **OpenTelemetry (OTel)** engine. It provides:
1. **W3C Trace Context Standard**: Interoperable trace propagation across polyglot architectures (`traceparent` and `tracestate`).
2. **Trace-Log Correlation**: Automatic injection of `traceId` and `spanId` into SLF4J MDC (Mapped Diagnostic Context).
3. **Baggage Propagation**: Propagating cross-cutting business context (e.g. `merchant-id`, `customer-tier`) across RPC boundaries without mutating database schemas.
4. **Context Propagation**: Thread-safe propagation of span context across asynchronous executors, reactive pipelines, and message queues.

```
+-------------------------------------------------------------------------------------------------+
|                              The Golden Rules of Distributed Tracing                            |
|                                                                                                 |
|  1. Always Use Standard W3C Trace Context Headers: Enforce traceparent and tracestate for       |
|     interoperability across Spring Boot, Go, Node.js, and Envoy sidecars.                       |
|  2. Correlate Logs with Trace Context in MDC: Ensure all log entries include traceId and spanId |
|     so SREs can jump directly from a Grafana alert to a Jaeger trace to an Elasticsearch log.  |
|  3. Always Propagate Context Over Async Boundaries: ThreadLocal span context is lost when       |
|     spawning threads; use TaskDecorator or ContextSnapshotFactory to prevent orphaned spans.     |
|  4. Scope and Close All Spans & Baggage: Always use try-with-resources with Tracer.SpanInScope  |
|     and BaggageInScope to prevent ThreadLocal memory leaks in thread pools.                     |
|  5. Adopt Tail-Based Sampling for High-Volume Systems: Keep 100% of errors and 100% of slow      |
|     traces (P99 > 500ms) while discarding 99% of successful, fast traces at the collector.    |
+-------------------------------------------------------------------------------------------------+
```

---

## 2. Internal Working

### The Anatomy of a Distributed Trace

```
Trace (TraceID: 4bf92f3577b34da6a3ce929d0e0e4736)
 │
 ├── [Root Span: HTTP POST /api/v1/payments/checkout] (Duration: 120ms)
 │    ├── Attributes: http.status_code=200, http.method=POST
 │    │
 │    ├── [Child Span 1: fraud-evaluation-engine] (Duration: 25ms)
 │    │    ├── Attributes: fraud.risk.score=12, fraud.decision=APPROVED
 │    │    └── Events: rule.evaluation.started, rule.evaluation.completed
 │    │
 │    └── [Child Span 2: database-insert-ledger] (Duration: 45ms)
 │         └── Attributes: db.system=postgresql, db.statement=INSERT INTO ledger...
```

---

### W3C Trace Context Specification

The W3C Trace Context specification defines standard HTTP headers for trace propagation:

#### 1. `traceparent` (4 Fields, 55 Characters)
```
00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01
│  │                                │                │
│  │                                │                └── TraceFlags: 01 (Sampled)
│  │                                └── ParentID (SpanID): 64-bit Hex (16 chars)
│  └── TraceID: 128-bit Hex (32 chars)
└── Version: 00 (Current W3C Standard)
```

#### 2. `tracestate`
Contains vendor-specific routing state (e.g. `congo=t61rcWkgMzE,rojo=1`).

---

### The Baggage API

Baggage propagates key-value pairs across distributed service boundaries alongside the trace context:

```
[Service A: Payment Gateway] ──► Injects Baggage: merchant-id=MERCH-994
            │
            ▼ (HTTP / gRPC with baggage header: merchant-id=MERCH-994)
[Service B: Fraud Check]     ──► Reads Baggage: merchant-id=MERCH-994 (Zero DB Queries!)
            │
            ▼
[Service C: Ledger Service]  ──► Reads Baggage: merchant-id=MERCH-994
```

---

### The Broken Context Propagation Trap (Async Thread Pools)

Because tracing context is stored in **`ThreadLocal`**, submitting a task to a `ThreadPoolExecutor` or `CompletableFuture.supplyAsync()` strips the span context unless explicitly propagated:

```
[Thread-1: Tomcat Request Thread] (Trace: 4bf92f35... Span: 00f067aa...)
            │
            ▼ ExecutorService.submit(runnable)
[Thread-2: Worker Pool Thread]    (ThreadLocal is EMPTY! -> Creates Orphan Root Trace!)
```

#### Solution: `TaskDecorator` with `ContextSnapshotFactory`
```java
@Configuration
public class AsyncTracingConfig {

    @Bean
    public ThreadPoolTaskExecutor paymentExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setTaskDecorator(runnable -> {
            // Captures current ThreadLocal trace context and propagates to worker thread
            ContextSnapshot snapshot = ContextSnapshotFactory.builder().build().captureAll();
            return snapshot.wrap(runnable);
        });
        executor.initialize();
        return executor;
    }
}
```

---

## 3. Enterprise Scenario: FinFlow 12-Hop Distributed Checkout Flow

In the **FinFlow Reference Architecture**:

```
Checkout Request ──► Ingress ──► Payment Gateway ──► Fraud Engine ──► Ledger ──► Bank Gateway
   (Trace: 4bf9...)                (Span: 1)           (Span: 2)     (Span: 3)    (Span: 4)
                                       │                   │             │            │
                                       ▼                   ▼             ▼            ▼
                         [All 4 services emit logs with matching [payment-service,4bf9...,<spanId>]]
                                       │
                                       ▼ (Exported via OTLP gRPC)
                               OpenTelemetry Collector ──► Tempo / Jaeger
```

---

## 4. Incorrect Implementation

Below is a flawed implementation demonstrating ThreadLocal context loss and memory leakage:

```java
package com.finflow.chapter330.incorrect;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Spawning threads without context propagation breaks the trace graph.
 * 2. Forgetting to end() spans causes memory leaks and incomplete trace durations.
 */
@Service
public class PaymentTracingServiceIncorrect {

    private final Tracer tracer;
    private final ExecutorService executor = Executors.newFixedThreadPool(10);

    public PaymentTracingServiceIncorrect(Tracer tracer) {
        this.tracer = tracer;
    }

    public void processPaymentFlawed(String paymentId) {
        Span parent = tracer.nextSpan().name("parent-operation").start();

        // BUG 1: ThreadLocal is NOT propagated into CompletableFuture -> Orphan trace!
        CompletableFuture.runAsync(() -> {
            // This runs with NO trace context in ThreadLocal!
            // Logs emitted here will have empty [,,] traceId!
            System.out.println("Processing async ledger update for: " + paymentId);
        }, executor);

        // BUG 2: parent.end() is NEVER called if an exception occurs -> Memory leak!
    }
}
```

---

## 5. Production Incident

### Incident Timeline

| Time (UTC) | Event / Telemetry |
|---|---|
| **00:00:00** | A deployment migrates payment fraud checks to asynchronous worker threads using uninstrumented `CompletableFuture.supplyAsync()`. |
| **00:15:00** | Ingress checkouts begin experiencing intermittent **10-second latency spikes**, causing client checkout timeouts. |
| **00:30:00** | SREs inspect distributed traces in Jaeger: The root trace shows 10-second total duration, but the span graph has **no child spans** (child spans are missing due to ThreadLocal context loss). |
| **01:00:00** | Because trace IDs were stripped from worker threads, application logs emitted by the fraud engine showed `[payment-service,,]` with empty trace IDs, making log correlation impossible. |
| **12:00:00** | Engineering spends 18 hours manually matching timestamps across 12 services and 500 pods. |
| **18:00:00** | SREs finally discover an uninstrumented third-party KYC fraud API hanging on socket timeouts. |
| **20:00:00** | Total business impact: **$14.2M in abandoned checkout carts** during the 20-hour investigation. |
| **21:00:00** | SREs deploy `TaskDecorator` with `ContextSnapshotFactory` and configure OpenTelemetry auto-instrumentation, restoring 100% trace graph completeness. |

---

## 6. Logs & Diagnostics

### 1. Broken Logs (Without Context Propagation)
```text
2026-08-21T00:15:10.102Z INFO  [payment-service,,] 1 --- [pool-2-thread-4] c.f.c.service.FraudCheckService : Calling external KYC verification API...
2026-08-21T00:15:20.104Z ERROR [payment-service,,] 1 --- [pool-2-thread-4] c.f.c.service.FraudCheckService : KYC socket read timed out after 10000ms
```
*(Notice the empty `[payment-service,,]` — impossible to correlate with the incoming checkout request!)*

### 2. Correlated Logs (With Production Micrometer Tracing)
```text
2026-08-21T00:15:10.102Z INFO  [payment-service,4bf92f3577b34da6a3ce929d0e0e4736,517df7dbcc1927ec] 1 --- [payment-exec-4] c.f.c.service.FraudCheckService : Calling external KYC verification API...
2026-08-21T00:15:20.104Z ERROR [payment-service,4bf92f3577b34da6a3ce929d0e0e4736,517df7dbcc1927ec] 1 --- [payment-exec-4] c.f.c.service.FraudCheckService : KYC socket read timed out after 10000ms
```
*(Exact `traceId: 4bf92f3577b34da6a3ce929d0e0e4736` connects the log directly to the root checkout span!)*

---

## 7. Root Cause Analysis

```
+-------------------------------------------------------------------------------------------------+
|                               Tracing Outage Root Cause Chain                                   |
|                                                                                                 |
|  1. ThreadPool ThreadLocal Context Disconnection                                                |
|     └── Submitting tasks to uninstrumented thread pools stripped traceparent context.           |
|                                                                                                 |
|  2. Incomplete Span Trees (Orphaned Spans)                                                      |
|     └── Async work created disconnected root traces or omitted child spans entirely.            |
|                                                                                                 |
|  3. Log MDC Stripping                                                                           |
|     └── Application logs lacked traceId and spanId, preventing log-to-trace navigation.         |
|                                                                                                 |
|  4. Remediation: TaskDecorator Context Propagation + W3C Trace Context                          |
|     └── ContextSnapshotFactory guaranteed seamless ThreadLocal trace propagation across async.  |
+-------------------------------------------------------------------------------------------------+
```

---

## 8. Debugging Process

```
[1. Inspect Incoming Headers] Verify client sends: curl -v -H "traceparent: 00-4bf92f...-01" http://localhost:8080/...
       │
[2. Check Log Pattern in application.yml] Ensure: logging.pattern.level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
       │
[3. Validate Async Context Propagation] Verify all custom ThreadPoolTaskExecutors have TaskDecorators
       │
[4. Query OpenTelemetry Collector / Tempo] Filter by: trace_id = 4bf92f3577b34da6a3ce929d0e0e4736
       │
[5. Inspect Span Attributes] Verify child spans contain expected tags (e.g. fraud.decision, merchant.id)
```

---

## 9. Correct Implementation

### 1. Hardened Tracing Service: `PaymentTracingService.java`

```java
package com.finflow.chapter330.service;

import com.finflow.chapter330.domain.PaymentTraceRequest;
import com.finflow.chapter330.domain.TraceDiagnostics;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.BaggageManager;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentTracingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentTracingService.class);

    private final Tracer tracer;
    private final BaggageManager baggageManager;
    private final FraudCheckTracingService fraudCheckService;

    public PaymentTracingService(Tracer tracer,
                                 BaggageManager baggageManager,
                                 FraudCheckTracingService fraudCheckService) {
        this.tracer = tracer;
        this.baggageManager = baggageManager;
        this.fraudCheckService = fraudCheckService;
    }

    public TraceDiagnostics processPayment(PaymentTraceRequest request) {
        long startTime = System.currentTimeMillis();

        Span currentSpan = tracer.currentSpan();
        String traceId = currentSpan != null ? currentSpan.context().traceId() : "NO_TRACE";
        String spanId = currentSpan != null ? currentSpan.context().spanId() : "NO_SPAN";

        // Keep Baggage active throughout the entire downstream transaction execution
        BaggageInScope baggageScope = null;
        if (request.getMerchantId() != null) {
            baggageScope = baggageManager.createBaggageInScope("merchant-id", request.getMerchantId());
        }

        try {
            if (baggageScope != null) {
                log.info("Processing checkout payment: {} | Merchant Baggage: {}",
                        request.getPaymentId(), baggageScope.get());
            }

            // Delegate to child traced service
            double amountVal = request.getAmount() != null ? request.getAmount().doubleValue() : 0.0;
            String fraudDecision = fraudCheckService.evaluateFraudRisk(request.getMerchantId(), amountVal);

            long duration = System.currentTimeMillis() - startTime;

            Baggage merchantBaggage = baggageManager.getBaggage("merchant-id");
            String baggageVal = merchantBaggage != null ? merchantBaggage.get() : request.getMerchantId();

            return new TraceDiagnostics(traceId, spanId, baggageVal, fraudDecision, duration);
        } finally {
            if (baggageScope != null) {
                baggageScope.close();
            }
        }
    }
}
```

---

### 2. Child Span Instrumented Service: `FraudCheckTracingService.java`

```java
package com.finflow.chapter330.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FraudCheckTracingService {

    private static final Logger log = LoggerFactory.getLogger(FraudCheckTracingService.class);
    private final Tracer tracer;

    public FraudCheckTracingService(Tracer tracer) {
        this.tracer = tracer;
    }

    public String evaluateFraudRisk(String merchantId, double amount) {
        // Create custom child span for fraud analysis
        Span childSpan = tracer.nextSpan().name("fraud-evaluation-engine").start();

        try (Tracer.SpanInScope spanInScope = tracer.withSpan(childSpan)) {
            childSpan.tag("fraud.risk.threshold", "85");
            childSpan.tag("merchant.id", merchantId);
            childSpan.tag("transaction.amount", String.valueOf(amount));
            childSpan.event("rule.evaluation.started");

            log.info("Executing rule-based fraud check for merchant: {} | Amount: {}", merchantId, amount);

            Thread.sleep(15);

            String decision = amount > 10000.0 ? "MANUAL_REVIEW" : "APPROVED";
            childSpan.tag("fraud.decision", decision);
            childSpan.event("rule.evaluation.completed");

            return decision;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            childSpan.error(e);
            return "REJECTED";
        } finally {
            childSpan.end();
        }
    }
}
```

---

### 3. Application Tracing Configuration: `application.yml`

```yaml
server:
  port: 8080

spring:
  application:
    name: payment-service

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
  tracing:
    sampling:
      probability: 1.0 # 100% sampling for development/test; 0.05 (5%) in production
    propagation:
      type: W3C # Enforces standard W3C traceparent and tracestate
    baggage:
      correlation:
        enabled: true
        fields:
          - merchant-id
          - customer-tier
      remote-fields:
        - merchant-id
        - customer-tier

logging:
  pattern:
    level: "%5p [${spring.application.name:},%X{traceId:-},%X{spanId:-}]"
```

---

## 10. Performance Comparison

Benchmarked across 12-hop microservice architecture handling 20,000 checkout req/sec.

| Metric | Without Context Propagation | Production OpenTelemetry Tracing |
|---|---|---|
| **Mean Time to Detect (MTTD)** | $> 45\text{ minutes}$ | **$< 30\text{ seconds}$** |
| **Mean Time to Resolve (MTTR)** | $> 18\text{ hours}$ | **$< 8\text{ minutes}$** |
| **Trace Context Retention across Async** | $0\%$ *(Stripped)* | **$100\%$ (Guaranteed via `TaskDecorator`)** |
| **MDC Log-to-Trace Match Rate** | $< 12\%$ | **$100\%$ Exact Correlation** |
| **Latency Overhead per Request** | $0\text{ms}$ | **$< 0.45\text{ms}$ (OTel OTLP batching)** |

---

## 11. Best Practices

### The Do's
- **DO use W3C Trace Context**: Guarantees seamless propagation across microservices in any programming language.
- **DO inject `traceId` and `spanId` into SLF4J MDC**: Enables instant log correlation in centralized logging tools (Elasticsearch, Loki).
- **DO always close spans and scopes**: Use `try (Tracer.SpanInScope ws = tracer.withSpan(span)) { ... } finally { span.end(); }`.
- **DO use `TaskDecorator` for thread pools**: Guarantees trace propagation across `@Async` and worker executors.
- **DO restrict Baggage keys to small, essential identifiers**: e.g., `tenant-id`, `merchant-id`.

### The Don'ts
- **DON'T store large payloads or PII in Baggage**: Baggage travels in every HTTP header, consuming network bandwidth and risking data leakage.
- **DON'T forget `span.end()`**: Unclosed spans will not be reported to the collector and leak memory.
- **DON'T rely on 100% head-based sampling in high-throughput production**: Use 1–5% probabilistic sampling or tail-based sampling in the OpenTelemetry Collector.

---

## 12. Common Mistakes

### Mistake 1: The Leaked `SpanInScope`
```java
Span span = tracer.nextSpan().name("my-span").start();
tracer.withSpan(span); // BUG: Never closed!
// Execution continues...
span.end();
```
**Why it fails**: `withSpan()` binds the span to the current `ThreadLocal`. When Tomcat reuses the worker thread for subsequent requests, the stale span context contaminates unrelated transactions.
**Production Fix**: Always wrap `withSpan()` in a try-with-resources statement:
```java
Span span = tracer.nextSpan().name("my-span").start();
try (Tracer.SpanInScope ws = tracer.withSpan(span)) {
    // work
} finally {
    span.end();
}
```

### Mistake 2: The Premature Baggage Closure
```java
try (BaggageInScope b = baggageManager.createBaggageInScope("merchant-id", "123")) {
    log.info("Started");
} // Baggage closed here!
downstreamClient.call(); // BUG: Baggage is already detached from context!
```
**Production Fix**: Ensure the `BaggageInScope` remains open for the full duration of the downstream RPC call.

---

## 13. Interview Questions

### Junior Tier
**Q: What is the difference between a Trace and a Span in distributed tracing?**
> **Answer**: 
> - **Trace**: Represents the entire journey of a request as it traverses through all services in a distributed system. It is uniquely identified by a 128-bit `traceId`.
> - **Span**: Represents a single unit of contiguous work within that trace (e.g. an HTTP request, a database query, or a method execution). A span has a name, start time, duration, `spanId`, optional `parentId`, attributes (tags), and events.

### Mid Tier
**Q: Explain the structure of the W3C `traceparent` HTTP header.**
> **Answer**: The W3C `traceparent` header is a 4-part, hyphen-separated string of 55 hexadecimal characters:
> `version-trace_id-parent_id-trace_flags`
> 1. **Version (2 hex chars)**: `00` (current standard).
> 2. **Trace ID (32 hex chars)**: 128-bit globally unique identifier for the entire distributed trace.
> 3. **Parent ID / Span ID (16 hex chars)**: 64-bit identifier for the calling span.
> 4. **Trace Flags (2 hex chars)**: 8-bit bitmap; `01` indicates the trace is recorded/sampled, `00` indicates not sampled.

### Senior Tier
**Q: Why does distributed trace context get lost in asynchronous Spring Boot code, and how do you fix it?**
> **Answer**: Micrometer Tracing and OpenTelemetry store active span context in Java `ThreadLocal` variables. When asynchronous code is executed (via `@Async`, `CompletableFuture`, or custom `ThreadPoolExecutor`), the task executes on a different thread where the `ThreadLocal` map is empty. This disconnects child spans and strips `traceId` from MDC logs.
> **Fix**: Configure a `TaskDecorator` on the `ThreadPoolTaskExecutor` using `ContextSnapshotFactory.builder().build().captureAll()`, which captures the caller's `ThreadLocal` trace context and attaches it to the worker thread before executing the task.

### Staff Tier
**Q: Compare Head-Based Sampling vs Tail-Based Sampling in a financial system processing 100,000 transactions/second.**
> **Answer**: 
> 1. **Head-Based Sampling**: The sampling decision is made at the root ingress node before the request executes (e.g. 1% random probabilistic sampling).
>    - *Advantage*: Minimal overhead, low memory usage.
>    - *Disadvantage*: Critical rare errors (0.01% payment failures) and P99 latency spikes are dropped with 99% probability, leaving SREs with no trace data for actual outages.
> 2. **Tail-Based Sampling**: The root service samples 100% of spans. All spans are streamed to an intermediate **OpenTelemetry Collector cluster** which buffers traces in memory for 10–30 seconds. The collector evaluates the completed trace:
>    - If `http.status_code >= 500` or `error == true` $\to$ **Keep 100%**.
>    - If `duration > 500ms` (P99 latency) $\to$ **Keep 100%**.
>    - If healthy and fast $\to$ Sample at **0.1%**.
>    - *Result*: Zero telemetry blind spots during incidents while reducing storage costs by 95%.

### Principal Tier
**Q: Design an Enterprise Observability Pipeline integrating OpenTelemetry Tracing, Prometheus Metrics, and Centralized Logging with Exemplar correlation for 1,000 microservices.**
> **Answer**: A Principal-level architecture uses **The LGTM Stack (Loki, Grafana, Tempo, Mimir) + OpenTelemetry Collectors**:
> 1. **Client Tier**: Spring Boot microservices instrumented with Micrometer Tracing & OTel Bridge export OTLP gRPC telemetry to local DaemonSet / Sidecar OTel Collectors.
> 2. **Collector Mesh**: OTel Collector evaluates tail-based sampling rules, strips sensitive PII from span attributes, and routes data to respective distributed backends.
> 3. **Exemplar Integration**: Micrometer attaches the active `traceId` as an **Exemplar** to Prometheus / Mimir histogram buckets.
> 4. **Seamless Grafana Navigation**:
>    - In Grafana, clicking a spike on a P99 latency metric dashboard displays the exact `traceId` Exemplar.
>    - Clicking the Exemplar navigates directly to the full distributed waterfall trace in **Grafana Tempo**.
>    - Clicking a span in Tempo queries **Grafana Loki** logs filtered by `traceId`, showing correlated line-by-line application logs in sub-seconds.

---

## 14. Hands-on Exercise

### Objective
Implement an asynchronous `TaskDecorator` that propagates Micrometer Tracing context across a custom `ThreadPoolTaskExecutor`.

### Solution

```java
@Configuration
@EnableAsync
public class AsyncTracingConfiguration implements AsyncConfigurer {

    @Bean(name = "tracedExecutor")
    public ThreadPoolTaskExecutor tracedExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(8);
        executor.setMaxPoolSize(32);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("traced-exec-");
        
        // Propagate trace and MDC context to async worker threads
        executor.setTaskDecorator(runnable -> {
            ContextSnapshot snapshot = ContextSnapshotFactory.builder().build().captureAll();
            return () -> {
                try (ContextSnapshot.Scope scope = snapshot.setThreadLocals()) {
                    runnable.run();
                }
            };
        });
        
        executor.initialize();
        return executor;
    }
}
```

---

## 15. Advanced Challenge: OpenTelemetry Collector Tail-Based Sampling Pipeline

### Enterprise Problem Statement
Write an `otel-collector-config.yaml` pipeline that keeps 100% of error traces and slow payment traces ($> 500\text{ms}$), while sampling normal traffic at 1%.

### Enterprise Solution

```yaml
receivers:
  otlp:
    protocols:
      grpc:
        endpoint: 0.0.0.0:4317

processors:
  tail_sampling:
    decision_wait: 10s
    num_traces: 100000
    expected_new_traces_per_sec: 5000
    policies:
      # Policy 1: Always retain error traces
      - name: drop-errors-policy
        type: status_code
        status_code: { status_codes: [ERROR] }

      # Policy 2: Always retain slow traces (> 500ms)
      - name: latency-policy
        type: latency
        latency: { threshold_ms: 500 }

      # Policy 3: Sample remaining healthy traces at 1%
      - name: probabilistic-policy
        type: probabilistic
        probabilistic: { sampling_percentage: 1.0 }

exporters:
  otlp/tempo:
    endpoint: tempo:4317
    tls:
      insecure: true

service:
  pipelines:
    traces:
      receivers: [otlp]
      processors: [tail_sampling]
      exporters: [otlp/tempo]
```

---

## 16. Production Checklist

Before approving any distributed tracing pull request:

- [ ] **W3C Trace Context Propagation Configured**: Confirm `management.tracing.propagation.type: W3C`.
- [ ] **MDC Logging Pattern Configured**: Ensure logback pattern includes `%X{traceId:-}` and `%X{spanId:-}`.
- [ ] **Async Executors Instrumented**: Verify all custom thread pools use `TaskDecorator` with `ContextSnapshotFactory`.
- [ ] **Spans & Baggage Properly Closed**: Verify `try-with-resources` or `finally` blocks close `SpanInScope` and `BaggageInScope`.
- [ ] **Sampling Rate Configured for Production**: Confirm production sampling is set to 1–5% (or tail-based collector is deployed).
- [ ] **No Sensitive PII in Baggage**: Ensure card numbers, passwords, or personal data are never attached as baggage.
