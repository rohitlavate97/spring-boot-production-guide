# Module 15: Logging, Observability, MDC & Tracing Gaps

## Overview
This module explores enterprise logging performance, Mapped Diagnostic Context (MDC) propagation across asynchronous thread boundaries using Spring's `TaskDecorator`, distributed tracing correlation (`X-Correlation-ID` & W3C `traceparent`), and structured logging architectures.

## Key Scenarios Covered
1. **Asynchronous MDC Context Loss:**
   - Why SLF4J MDC is lost when submitting tasks to `@Async` or `ThreadPoolTaskExecutor`, and how `TaskDecorator` guarantees seamless context inheritance.
2. **Distributed Correlation ID Propagation:**
   - Automatically capturing incoming `X-Correlation-ID` headers (or generating UUIDs) and populating MDC and HTTP response headers.
3. **Synchronous File Logging Contention:**
   - Explaining lock contention in Logback file/console appenders, and configuring `AsyncAppender` with bounded queues and zero-drop discarding thresholds (`discardingThreshold: 0`).
4. **Structured JSON Logging & Tracing Integration:**
   - Standardizing log output format for centralized ingestion (Datadog, Splunk, Elastic/ELK, Grafana Loki).

## Project Structure
- `src/main/java/.../filter/`: `CorrelationIdFilter.java` (Extracts/generates `X-Correlation-ID`, manages MDC lifecycle).
- `src/main/java/.../decorator/`: `MdcTaskDecorator.java` (Copies MDC context map to worker threads).
- `src/main/java/.../config/`: `ObservabilityThreadPoolConfig.java`.
- `src/main/java/.../service/`: `OrderProcessingService.java` (`@Async` order processing with MDC inspection).
- `src/main/java/.../controller/`: `OrderController.java`.
- `src/test/java/.../`:
  - `CorrelationIdFilterTest.java`
  - `MdcPropagationAsyncTest.java`
  - `Module15IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full 12-section technical incident guide, see [Module 15 Documentation](../../docs/module-15-logging-observability-mdc-tracing.md).
