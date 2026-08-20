---
chapter: 320
topic: Micrometer, Prometheus & Grafana — Custom Metrics, Dashboards, Alerting, SLOs
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240, 250, 260, 270, 280, 290, 300, 310]
reference_system_node: Payment Service Telemetry Engine ↔ Micrometer & Prometheus MeterRegistry (MeterRegistry, Counter, Timer, Gauge, DistributionSummary, High-Cardinality Prevention, SLOs, PromQL)
---

# Chapter 320: Micrometer, Prometheus & Grafana — Custom Metrics, Dashboards, Alerting, SLOs

## 1. Concept

In high-throughput distributed payment architectures, **Metrics** represent the first line of operational visibility. While logs explain *why* an individual transaction failed and distributed traces pinpoint *where* latency occurred, metrics provide real-time aggregate health indicators across thousands of microservice instances.

Spring Boot integrates with **Micrometer**, an application metrics facade that acts as the "SLF4J of metrics". Micrometer decouples your application code from specific monitoring backends, allowing seamless exposition to **Prometheus**, Datadog, InfluxDB, or AWS CloudWatch.

However, improper metric instrumentation creates catastrophic monitoring failures:
1. **The High-Cardinality Dimensionality Explosion**: Adding dynamic tags (e.g. `user_id`, `transaction_id`, `card_number`) creates millions of unique time series, crashing the Prometheus TSDB with out-of-memory errors and blinding SREs during outages.
2. **The Gauge WeakReference Trap**: Registering gauges against transient objects that get garbage collected, causing metrics to report misleading `NaN` or zero values.
3. **Misconfigured Percentiles**: Relying on inaccurate client-side averages instead of PromQL histogram quantiles (`histogram_quantile(0.99, ...)`).

```
+-------------------------------------------------------------------------------------------------+
|                                 The Golden Rules of Production Metrics                          |
|                                                                                                 |
|  1. Never Use Dynamic or Unbounded Values as Metric Tags: Tags must strictly have LOW           |
|     CARDINALITY (e.g. currency, status, payment_method, error_code). Never use transaction_id,  |
|     user_id, or timestamps.                                                                     |
|  2. Choose the Right Meter for the Job:                                                         |
|     • Counter: Monotonically increasing counts (requests, errors, bytes).                       |
|     • Timer: Short latencies + invocation counts (HTTP endpoints, database queries).            |
|     • Gauge: Instantaneous point-in-time values (in-flight connections, queue size).            |
|     • DistributionSummary: Distribution of non-time values (payload bytes, payment amounts).   |
|  3. Enable Percentile Histograms for Latency SLOs: Use publishPercentileHistogram(true) or      |
|     explicit serviceLevelObjectives(100ms, 300ms, 500ms) for accurate PromQL p95/p99 queries.    |
|  4. Hold Strong References to Gauged Objects: Micrometer maintains WeakReferences to gauged    |
|     targets; transient objects will be garbage collected, breaking metric collection.           |
+-------------------------------------------------------------------------------------------------+
```

---

## 2. Internal Working

### The 4 Core Meter Types in Micrometer

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           Micrometer Meter Types                            │
│                                                                             │
│  1. Counter             ──► Monotonically increasing count                  │
│                             e.g. payment_transactions_total                 │
│                                                                             │
│  2. Timer               ──► Measures latency distribution & frequency       │
│                             e.g. payment_processing_duration_seconds        │
│                                                                             │
│  3. Gauge               ──► Instantaneous point-in-time value               │
│                             e.g. payment_inflight_requests                  │
│                                                                             │
│  4. DistributionSummary ──► Distribution of non-time numeric quantities     │
│                             e.g. payment_transaction_amount_USD             │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

### The High-Cardinality Dimensionality Explosion

In time-series databases like Prometheus, every unique combination of metric name and key-value tag pairs forms an independent **Time Series**:

$$\text{Total Time Series} = \prod_{i=1}^{N} |\text{Tag}_i|$$

```
DANGEROUS (High Cardinality):
payment_transactions_total{status="SUCCESS", currency="USD", transaction_id="TX-948102"}
                                                                 ▲
                                                                 │ 10,000,000 unique IDs
                                                                 ▼
                                                  10,000,000 Time Series Created in Prometheus!
                                                  (Prometheus Head Block Memory Exhaustion!)

CORRECT (Low Cardinality):
payment_transactions_total{status="SUCCESS", currency="USD", payment_method="CREDIT_CARD"}
   (3 statuses)      ×      (5 currencies)     ×       (4 payment methods) = 60 Total Time Series!
```

---

### Percentile Histograms & PromQL Quantiles

To calculate accurate percentiles ($P_{50}$, $P_{95}$, $P_{99}$) across 50 Kubernetes pods in Prometheus, Micrometer must publish histogram buckets:

```yaml
management:
  metrics:
    distribution:
      percentiles-histogram:
        payment.processing.duration: true
      slo:
        payment.processing.duration: 100ms, 300ms, 500ms
```

This exports bucketed metrics in Prometheus format:
```text
payment_processing_duration_seconds_bucket{le="0.1"} 1420
payment_processing_duration_seconds_bucket{le="0.3"} 1980
payment_processing_duration_seconds_bucket{le="0.5"} 2000
payment_processing_duration_seconds_bucket{le="+Inf"} 2000
payment_processing_duration_seconds_count 2000
payment_processing_duration_seconds_sum 245.8
```

#### PromQL P99 Latency Calculation Query
```promql
histogram_quantile(0.99, sum(rate(payment_processing_duration_seconds_bucket[5m])) by (le))
```

---

### Service Level Objectives (SLOs) & Multi-Burn-Rate Alerting

For a **99.9% Availability SLO** (Error Budget: 0.1% = 0.001):

```
┌─────────────────────────────────────────────────────────────────────────┐
│                      Multi-Window Multi-Burn-Rate Alerts                │
│                                                                         │
│  Fast Burn Alert (14.4x Burn Rate) ──► Consumes 2% of budget in 1 hour  │
│     • Condition: Error Rate > 1.44% over 1 hour                         │
│     • Action: Page On-Call Engineer (SEV-1)                             │
│                                                                         │
│  Slow Burn Alert (3x Burn Rate)    ──► Consumes 5% of budget in 6 hours │
│     • Condition: Error Rate > 0.3% over 6 hours                         │
│     • Action: Create High-Priority JIRA Ticket                          │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Enterprise Scenario: FinFlow Payment Processing Telemetry Fleet

In the **FinFlow Reference Architecture**:

```
Client Ingress (25,000 tx/sec) ──► payment-service (50 Pods)
                                         │
                                         ▼ (Micrometer MeterRegistry)
                           ┌──────────────────────────────┐
                           │ • Counter: transactions.total│
                           │ • Timer: processing.duration │
                           │ • Gauge: inflight.requests   │
                           │ • Summary: transaction.amount│
                           └──────────────────────────────┘
                                         │
                                         ▼
                           /actuator/prometheus Endpoint
                                         │
                                         ▼ (Scraped every 15s)
                           Prometheus Cluster ──► Grafana Dashboards & Alertmanager
```

---

## 4. Incorrect Implementation

Below is a vulnerable service demonstrating the high-cardinality disaster and gauge garbage collection bugs:

```java
package com.finflow.chapter320.incorrect;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. High-Cardinality dynamic tags create millions of time series -> Prometheus OOM.
 * 2. Gauge registered against transient local variable is garbage collected -> reports NaN.
 */
@Service
public class PaymentMetricsServiceIncorrect {

    private final MeterRegistry registry;

    public PaymentMetricsServiceIncorrect(MeterRegistry registry) {
        this.registry = registry;
    }

    public void processPaymentFlawed(String txId, String cardNumber, double amount) {
        // Flaw 1: Dynamic unique IDs in tags -> 10 million time series per day!
        Counter.builder("payment.flawed.transactions")
                .tag("transaction_id", txId)
                .tag("card_number", cardNumber)
                .register(registry)
                .increment();

        // Flaw 2: Transient local variable gauged -> WeakReference GC eviction!
        AtomicInteger transientQueue = new AtomicInteger(42);
        registry.gauge("transient.queue.size", transientQueue); // Will report NaN after GC!
    }
}
```

---

## 5. Production Incident

### Incident Timeline

| Time (UTC) | Event / Telemetry |
|---|---|
| **00:00:00** | A new feature release adds `order_id` as a tag to `payment.processing.duration` for detailed transaction debugging. |
| **00:30:00** | Black Friday flash sale starts. Ingress traffic surges to 22,000 checkout req/sec. |
| **01:00:00** | Prometheus scrapers pull 45 million unique time series within 90 minutes. |
| **01:15:00** | Prometheus TSDB Head Block exceeds 128GB of memory, crashing with Linux OOM. |
| **01:16:00** | Prometheus enters a crash loop (`Context deadline exceeded while scraping /actuator/prometheus`). |
| **01:20:00** | All Grafana dashboards turn blank. Alertmanager stops evaluating SLO alert rules. SREs are **completely blind** to platform health. |
| **01:30:00** | SEV-0 declared: Monitoring infrastructure blackout during highest traffic hour of the year. |
| **02:00:00** | SREs identify high-cardinality `order_id` tag in scrape payloads, deploy hotfix removing the tag, and purge Prometheus TSDB WAL. |
| **02:15:00** | Prometheus memory drops to 12GB; dashboards recover. Total monitoring downtime: 59 minutes. |

---

## 6. Logs & Diagnostics

### 1. Prometheus Server Crash Log
```text
2026-08-21T01:15:12.114Z level=error caller=main.go:812 msg="Out of memory in TSDB head block" err="runtime: out of memory: cannot allocate 8589934592-byte block (sys: 137438953472)"
2026-08-21T01:15:12.120Z level=fatal caller=main.go:820 msg="Prometheus TSDB corrupted due to high cardinality series churn"
```

### 2. Spring Boot High-Cardinality Scrape Log
```text
2026-08-21T01:05:00.842Z WARN [payment-service] 1 --- [http-nio-8080-exec-12] i.m.p.PrometheusMeterRegistry : The number of meters in the registry exceeded 500000. Potential high-cardinality tag detected: [transaction_id]
```

---

## 7. Root Cause Analysis

```
+-------------------------------------------------------------------------------------------------+
|                               Metrics Outage Root Cause Chain                                   |
|                                                                                                 |
|  1. High-Cardinality Tag Insertion                                                             |
|     └── Adding order_id / transaction_id created a distinct time series for EVERY payment.     |
|                                                                                                 |
|  2. Exponential Time Series Growth                                                              |
|     └── 22,000 req/sec created 45 million new time series in Prometheus TSDB in 90 minutes.    |
|                                                                                                 |
|  3. Prometheus TSDB Memory Exhaustion                                                           |
|     └── Head Block memory allocated 128GB RAM, triggering Linux OOM crash of Prometheus server. |
|                                                                                                 |
|  4. Remediation: MeterFilter Cardinality Clamping + Low-Cardinality Tags                        |
|     └── Restricting tags to [currency, payment_method, status] capped total series at 60.       |
+-------------------------------------------------------------------------------------------------+
```

---

## 8. Debugging Process

```
[1. Identify High-Cardinality Labels] Query: GET http://prometheus:9090/api/v1/status/tsdb
       │ Check "Top 10 Series Count by Metric Name" and "Top Label Names with Highest Cardinality"
       │
[2. Inspect Scrape Payload Size] Run: curl -s http://localhost:8080/actuator/prometheus | wc -l
       │
[3. Check Gauge References] Ensure gauged objects are stored in long-lived Spring beans (not locals)
       │
[4. Verify Histogram Buckets] Check le="..." label distribution in /actuator/prometheus
       │
[5. Rollout] Deploy MeterFilter and verify Grafana PromQL p99 latency queries
```

---

## 9. Correct Implementation

### 1. Production Metrics Configuration: `MetricsConfig.java`

```java
package com.finflow.chapter320.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("application", "payment-service", "region", "us-east-1")
                // Filter out high-overhead JVM metrics if not needed
                .meterFilter(MeterFilter.denyNameStartsWith("jvm.gc.memory.allocated"))
                // Enforce SLO boundaries and percentile histograms on payment timers
                .meterFilter(new MeterFilter() {
                    @Override
                    public io.micrometer.core.instrument.distribution.DistributionStatisticConfig configure(
                            io.micrometer.core.instrument.Meter.Id id,
                            io.micrometer.core.instrument.distribution.DistributionStatisticConfig config) {
                        if (id.getName().equals("payment.processing.duration")) {
                            return io.micrometer.core.instrument.distribution.DistributionStatisticConfig.builder()
                                    .percentilesHistogram(true)
                                    .serviceLevelObjectives(
                                            Duration.ofMillis(100).toNanos(),
                                            Duration.ofMillis(300).toNanos(),
                                            Duration.ofMillis(500).toNanos()
                                    )
                                    .build()
                                    .merge(config);
                        }
                        return config;
                    }
                });
    }
}
```

---

### 2. Hardened Metrics Service: `PaymentMetricsService.java`

```java
package com.finflow.chapter320.service;

import com.finflow.chapter320.domain.PaymentTransaction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PaymentMetricsService {

    private final MeterRegistry registry;
    private final AtomicInteger inFlightRequests = new AtomicInteger(0);
    private final DistributionSummary amountSummary;

    public PaymentMetricsService(MeterRegistry registry) {
        this.registry = registry;

        // Long-lived Gauge: Tracks current in-flight concurrent payment requests
        registry.gauge("payment.inflight.requests", inFlightRequests);

        // DistributionSummary: Measures distribution of payment amounts ($1 to $100,000)
        this.amountSummary = DistributionSummary.builder("payment.transaction.amount")
                .description("Distribution of payment amounts processed")
                .baseUnit("USD")
                .maximumExpectedValue(100_000.0)
                .register(registry);
    }

    public PaymentTransaction processPayment(PaymentTransaction transaction) {
        inFlightRequests.incrementAndGet();
        long startNanos = System.nanoTime();

        try {
            if (transaction.getLatencyMs() > 0) {
                Thread.sleep(transaction.getLatencyMs());
            }

            if (transaction.getAmount() != null) {
                amountSummary.record(transaction.getAmount().doubleValue());
            }

            // Counter: Monotonically increasing count with LOW-CARDINALITY tags
            Counter.builder("payment.transactions.total")
                    .tag("currency", transaction.getCurrency() != null ? transaction.getCurrency() : "USD")
                    .tag("payment_method", transaction.getPaymentMethod() != null ? transaction.getPaymentMethod() : "CREDIT_CARD")
                    .tag("status", transaction.getStatus() != null ? transaction.getStatus() : "SUCCESS")
                    .register(registry)
                    .increment();

            return transaction;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            transaction.setStatus("FAILED");
            return transaction;
        } finally {
            inFlightRequests.decrementAndGet();
            long durationNanos = System.nanoTime() - startNanos;

            // Timer: Latency distribution
            Timer.builder("payment.processing.duration")
                    .tag("payment_method", transaction.getPaymentMethod() != null ? transaction.getPaymentMethod() : "CREDIT_CARD")
                    .tag("status", transaction.getStatus() != null ? transaction.getStatus() : "SUCCESS")
                    .register(registry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    public int getInFlightRequests() {
        return inFlightRequests.get();
    }
}
```

---

### 3. Application Telemetry Configuration: `application.yml`

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
  endpoint:
    prometheus:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
      region: us-east-1
      environment: prod
    distribution:
      percentiles-histogram:
        payment.processing.duration: true
        http.server.requests: true
      slo:
        payment.processing.duration: 100ms, 300ms, 500ms
        http.server.requests: 100ms, 300ms, 500ms
```

---

## 10. Performance Comparison

Benchmarked on a cluster handling 25,000 tx/sec over 24 hours.

| Metric | High-Cardinality Tags (with `order_id`) | Low-Cardinality Production Tuning |
|---|---|---|
| **Prometheus Time Series Count** | $45,000,000+$ *(Crash)* | **$< 2,500$ Total Series** |
| **Prometheus Scrape Payload** | $> 850\text{ MB}$ / scrape *(Timeout)* | **$< 180\text{ KB}$ / scrape** |
| **Prometheus Server Memory** | $128\text{ GB}+$ *(OOMKilled)* | **$\approx 4.5\text{ GB}$ Stable** |
| **P99 Latency Query Speed** | $> 45\text{s}$ *(Timeout)* | **$< 15\text{ms}$** |
| **SLO Alerting Reliability** | 0% *(Blackout during outages)* | **100% (Sub-second alert trigger)** |

---

## 11. Best Practices

### The Do's
- **DO enforce low cardinality on all metric tags**: Keep distinct values per tag under 50.
- **DO use `Counter` for event rates and `Timer` for latency**: Never use Gauge to measure request durations.
- **DO enable `publishPercentileHistogram(true)`**: Allows server-side `histogram_quantile()` in PromQL.
- **DO define SLO boundaries (`serviceLevelObjectives`)**: Enables exact error budget calculations.
- **DO hold strong references to objects wrapped by Gauges**: Store gauged objects in Spring singleton beans.

### The Don'ts
- **DON'T put user IDs, order IDs, UUIDs, or timestamps in metric tags**: Explodes time-series cardinality.
- **DON'T create new meter builders inside tight loops**: Register meters once during bean initialization or let Micrometer cache by `Meter.Id`.
- **DON'T use `Timer` for long-running batch jobs**: Use `LongTaskTimer` for tasks lasting minutes/hours.
- **DON'T compute percentiles on the client without histogram buckets**: Prevents accurate aggregation across multiple instances.

---

## 12. Common Mistakes

### Mistake 1: The Ephemeral Gauge WeakReference Eviction
```java
public void trackQueue(Queue queue) {
    registry.gauge("queue.size", queue, Queue::size); // BUG: If queue is transient, GC cleans it!
}
```
**Why it fails**: Micrometer's `Gauge.builder` maintains a `WeakReference` to the state object. When garbage collection runs, the object is collected, and the gauge silently starts returning `NaN`.
**Production Fix**: Store the tracked queue or `AtomicInteger` in a persistent Spring `@Service` field.

### Mistake 2: The `Counter` vs `Gauge` Misconception
Using a `Gauge` to count total errors or total requests.
**Why it fails**: Gauges fluctuate up and down and cannot be aggregated across pods with PromQL `rate()` or `increase()`.
**Production Fix**: Always use `Counter.increment()` for countable events.

---

## 13. Interview Questions

### Junior Tier
**Q: What is the difference between a Counter and a Gauge in Micrometer?**
> **Answer**: 
> - **Counter**: A monotonically increasing cumulative metric that only goes up (or resets to zero on application restart). Used to track counts of events (e.g. `http_requests_total`, `payment_failures_total`). In Prometheus, counters are queried using the `rate()` function.
> - **Gauge**: An instantaneous point-in-time value that can arbitrarily go up or down (e.g. current CPU usage, active thread pool workers, in-flight HTTP connections).

### Mid Tier
**Q: What is the High Cardinality problem in Prometheus, and how does it impact production?**
> **Answer**: High cardinality occurs when metric tags contain unbounded or highly dynamic values (e.g. user IDs, credit card numbers, UUIDs, timestamps). In Prometheus, every unique combination of tag key-values creates an entirely new time series. If an application processes millions of requests with unique tags, millions of time series are written to the Prometheus TSDB Head Block. This consumes gigabytes of RAM, causes scrape timeouts, inflates storage, and inevitably crashes the Prometheus monitoring server with OutOfMemory errors.

### Senior Tier
**Q: How do you configure Micrometer and Prometheus to accurately calculate the P99 latency across 100 Kubernetes pods?**
> **Answer**: Calculating accurate global percentiles across multiple distributed pods requires **histogram buckets**, because client-side percentiles cannot be mathematically averaged across nodes. In Spring Boot:
> 1. Enable histogram publication via `management.metrics.distribution.percentiles-histogram.payment.processing.duration=true` or via a `MeterFilter` with explicit `serviceLevelObjectives()`.
> 2. Micrometer exports the durations into cumulative bucket counters (`_bucket{le="0.1"}`).
> 3. In Grafana / Prometheus, calculate the cluster-wide P99 latency using PromQL:
>    ```promql
>    histogram_quantile(0.99, sum(rate(payment_processing_duration_seconds_bucket[5m])) by (le))
>    ```

### Staff Tier
**Q: Design a Multi-Burn-Rate Alerting Strategy in Prometheus for a 99.99% Availability SLO.**
> **Answer**: For a 99.99% SLO, the error budget is $0.01\% = 0.0001$:
> 1. **Fast Burn Alert (14.4x rate, 1h window)**: Consumes 2% of the budget in 1 hour. Trigger alert if error rate $> 0.144\%$ over 1 hour and 5 minutes:
>    ```promql
>    (sum(rate(http_requests_total{status=~"5.."}[1h])) / sum(rate(http_requests_total[1h]))) > (14.4 * 0.0001)
>    and
>    (sum(rate(http_requests_total{status=~"5.."}[5m])) / sum(rate(http_requests_total[5m]))) > (14.4 * 0.0001)
>    ```
>    *Action*: Page On-Call immediately (SEV-1).
> 2. **Slow Burn Alert (3x rate, 6h window)**: Consumes 5% of the budget in 6 hours ($> 0.03\%$).
>    *Action*: Ticket notification to engineering team.

### Principal Tier
**Q: Design an Enterprise Centralized Telemetry Platform capable of handling 50,000,000 metrics/sec across 2,000 microservices.**
> **Answer**: A Principal-level architecture uses **Thanos / Cortex / Mimir with OpenTelemetry Collector Agents**:
> 1. **Agent Tier**: Local OpenTelemetry Collector sidecars in each Kubernetes pod scrape `/actuator/prometheus`, enforce strict `MeterFilter` tag dropping, batch metrics, and export via OTLP gRPC.
> 2. **Ingestion & Storage (Grafana Mimir / Cortex)**: Distributed stateless Ingesters write to a DynamoDB/Cassandra index and S3 object storage for long-term retention.
> 3. **Query Engine**: Distributed Query-Frontend with query caching, split-and-merge parallelization, and sub-second PromQL evaluation over petabyte-scale metric datasets.
> 4. **Automated Cardinality Guardrails**: Ingestion rate limiters and rule-based cardinal drop filters automatically quarantine any microservice violating tag cardinality policies before Prometheus can be impacted.

---

## 14. Hands-on Exercise

### Objective
Implement a custom `MeterBinder` that binds ThreadPoolTaskExecutor metrics (active threads, pool size, queue capacity) into Micrometer.

### Solution

```java
@Component
public class CustomThreadPoolMetricsBinder implements MeterBinder {

    private final ThreadPoolTaskExecutor executor;

    public CustomThreadPoolMetricsBinder(ThreadPoolTaskExecutor executor) {
        this.executor = executor;
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("custom.threadpool.active", executor, ThreadPoolTaskExecutor::getActiveCount)
                .description("Active threads in executor")
                .register(registry);

        Gauge.builder("custom.threadpool.queue.size", executor, e -> e.getThreadPoolExecutor().getQueue().size())
                .description("Current queue size in executor")
                .register(registry);
    }
}
```

---

## 15. Advanced Challenge: Multi-Burn-Rate Prometheus Alerting Rule

### Enterprise Problem Statement
Write a production `PrometheusRule` YAML manifest for Alertmanager implementing Multi-Window Multi-Burn-Rate alerting for a 99.9% Payment API SLO.

### Enterprise Solution

```yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: payment-service-slo-alerts
  namespace: finflow
spec:
  groups:
    - name: PaymentServiceSLO
      rules:
        # Fast Burn: 14.4x Burn Rate (2% budget consumed in 1h) -> PAGE
        - alert: PaymentServiceErrorBudgetFastBurn
          expr: |
            (
              sum(rate(payment_transactions_total{status="FAILED"}[1h]))
              /
              sum(rate(payment_transactions_total[1h]))
            ) > (14.4 * (1 - 0.999))
            and
            (
              sum(rate(payment_transactions_total{status="FAILED"}[5m]))
              /
              sum(rate(payment_transactions_total[5m]))
            ) > (14.4 * (1 - 0.999))
          for: 2m
          labels:
            severity: page
            tier: critical
          annotations:
            summary: "Payment Service 1-hour error budget burn rate is critical (14.4x)"
            description: "Error rate is currently {{ $value | humanizePercentage }}, burning 2% of 30-day budget per hour."
```

---

## 16. Production Checklist

Before approving any observability pull request:

- [ ] **No High-Cardinality Tags**: Confirm no user IDs, order IDs, UUIDs, or card numbers are tags.
- [ ] **Prometheus Actuator Exposed**: Verify `/actuator/prometheus` is exposed and scraping cleanly.
- [ ] **SLO Buckets Configured**: Confirm `percentiles-histogram: true` or explicit `slo` boundaries exist for key timers.
- [ ] **Common Tags Configured**: Confirm `application`, `region`, and `environment` are attached globally.
- [ ] **Gauges Strongly Referenced**: Verify gauged objects reside in long-lived Spring beans.
- [ ] **PromQL P99 Queries Validated**: Confirm Grafana dashboards use `histogram_quantile()`.
- [ ] **Multi-Burn-Rate Alerting Configured**: Confirm Alertmanager rules protect critical SLOs.
