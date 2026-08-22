# Actuator & Observability Production Checklist

## Hardening Spring Boot Actuator, Micrometer Metrics, OpenTelemetry Tracing & Structured Logging

---

### 1. Actuator Security & Endpoint Exposure

- [ ] **Never Expose Sensitive Endpoints to the Public Internet:**
  Ensure `management.endpoints.web.exposure.include` exposes ONLY safe operational endpoints (`health,info,metrics,prometheus`).
  ```yaml
  management:
    endpoints:
      web:
        exposure:
          include: health,info,metrics,prometheus
  ```
- [ ] **Disable Dangerous Remote Management Endpoints:**
  Ensure `env`, `beans`, `threaddump`, `heapdump`, `shutdown`, and `restart` are DISABLED or protected by internal VPC network policies and mutual TLS / OAuth2 authentication.
- [ ] **Isolate Actuator to a Dedicated Internal Port:**
  ```yaml
  management:
    server:
      port: 8081 # Actuator traffic separated from public traffic on 8080!
  ```

---

### 2. Kubernetes Probes & Health Indicator Hardening

- [ ] **Enable Dedicated Liveness and Readiness Groups:**
  ```yaml
  management:
    endpoint:
      health:
        probes:
          enabled: true
        group:
          liveness:
            include: livenessState
          readiness:
            include: readinessState,db,redis
  ```
- [ ] **Protect Liveness Probes from Cascading Outages:**
  Liveness probe must check ONLY internal JVM viability (`/actuator/health/liveness`). NEVER include downstream database, Kafka, or third-party API checks in the liveness probe.
- [ ] **Graceful Degradation for Readiness Probes:**
  Configure readiness probes with `failureThreshold: 3` and `timeoutSeconds: 3` to prevent transient network hiccups from dropping pods.

---

### 3. Micrometer & Prometheus Metric Instrumentation

- [ ] **HikariCP Connection Pool Metrics:**
  Verify that `hikaricp.connections.active`, `hikaricp.connections.idle`, and `hikaricp.connections.pending` are exported.
- [ ] **JVM Garbage Collection & Memory Metrics:**
  Track `jvm.memory.used{area="heap"}`, `jvm.memory.used{area="nonheap"}`, and `jvm.gc.pause`.
- [ ] **HTTP Server Request Percentiles & Histograms:**
  Enable SLA percentiles in `application.yml`:
  ```yaml
  management:
    metrics:
      distribution:
        percentiles-histogram:
          http.server.requests: true
        percentiles:
          http.server.requests: 0.5, 0.95, 0.99
  ```
- [ ] **Kafka Consumer Lag & Thread Pool Metrics:**
  Export `kafka.consumer.fetch.manager.records.lag` and `executor.active` for custom thread pools.

---

### 4. OpenTelemetry Distributed Tracing & MDC Propagation

- [ ] **W3C Trace Context Propagation:**
  Ensure incoming `traceparent` headers are propagated downstream via `RestTemplate`, `RestClient`, or `WebClient`.
- [ ] **Async Context Propagation:**
  Configure `TaskDecorator` or Micrometer Context Propagation to ensure `traceId` and `spanId` are not lost when switching threads in `@Async` or reactive flows.
- [ ] **Sampling Rate Strategy:**
  Set production trace sampling to $1\%\text{--}5\%$ (`management.tracing.sampling.probability: 0.05`) to prevent telemetry bandwidth saturation, while recording 100% of HTTP 5xx error spans.

---

### 5. Structured JSON Logging

- [ ] **Logback Logstash JSON Encoder:**
  Format logs as single-line JSON with standard fields (`@timestamp`, `level`, `logger`, `thread`, `traceId`, `spanId`, `message`).
- [ ] **Asynchronous Logging (`AsyncAppender`):**
  Ensure logging writes to an asynchronous in-memory ring buffer with `neverBlock: true` and `queueSize: 1024` to prevent I/O blocking.
- [ ] **Log Level Hardening:**
  Default root log level must be `INFO` or `WARN`. Never run `DEBUG` on high-throughput database or HTTP packages in production.

---

*(End of Actuator & Observability Checklist)*
