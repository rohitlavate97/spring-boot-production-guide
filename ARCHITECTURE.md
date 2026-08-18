# Reference Architecture — FinFlow Payment Platform

> This document defines the **single running reference system** used across all chapters of the
> Spring Boot Production Mastery Handbook. Every chapter zooms into one or more nodes of this
> architecture rather than inventing a new system from scratch. Amendments are appended — never
> overwritten — by later chapters.

---

## 1. System Topology

```
                              ┌──────────────────────────────────────────────────┐
                              │                  Kubernetes Cluster              │
                              │                                                  │
  Clients ──► Load Balancer ──┤  ┌──────────────┐                               │
              (L7 / Ingress)  │  │  API Gateway  │  (Spring Cloud Gateway)       │
                              │  └──────┬───────┘                               │
                              │         │                                        │
                              │    ┌────┴────────────────┬──────────────────┐    │
                              │    ▼                     ▼                  ▼    │
                              │  ┌──────────────┐ ┌──────────────┐ ┌───────────┐│
                              │  │Payment Service│ │Order Service │ │Ledger Svc ││
                              │  │  (20 pods)    │ │  (20 pods)   │ │ (10 pods) ││
                              │  └──────┬───────┘ └──────┬───────┘ └─────┬─────┘│
                              │         │                │               │       │
                              │    ┌────┴────┐      ┌────┴────┐    ┌────┴────┐  │
                              │    ▼         ▼      ▼         ▼    ▼         │  │
                              │ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐│  │
                              │ │Postgr│ │Redis │ │Postgr│ │ Kafka│ │Postgr││  │
                              │ │  SQL  │ │Cluster│ │  SQL  │ │Cluster│ │  SQL  ││  │
                              │ └──────┘ └──────┘ └──────┘ └──────┘ └──────┘│  │
                              │                              │               │  │
                              │                         ┌────┴─────┐         │  │
                              │                         ▼          ▼         │  │
                              │                   ┌───────────┐ ┌────────┐   │  │
                              │                   │Notification│ │Analytics│  │  │
                              │                   │  Service   │ │ Service │  │  │
                              │                   └───────────┘ └────────┘   │  │
                              │                                              │  │
                              └──────────────────────────────────────────────┘  │
                                                                                │
                                              ┌────────────────┐                │
                                              │ Third-Party     │◄───────────────┘
                                              │ Payment Gateway │
                                              │ (Stripe-like)   │
                                              └────────────────┘
```

---

## 2. Service Descriptions

### 2.1 API Gateway (Spring Cloud Gateway)
- **Role:** Single ingress point. Routes requests, enforces rate limits, strips/adds headers, terminates TLS.
- **Technology:** Spring Cloud Gateway on Spring Boot 3.x, Netty-based (reactive).
- **Stateless.** No database of its own. Reads route config from Spring Cloud Config Server.

### 2.2 Payment Service
- **Role:** Processes payment intents, charges, refunds, and settlement reconciliation.
- **Domain entities:** `PaymentIntent`, `Charge`, `Refund`, `PaymentMethod`, `Settlement`.
- **Database:** Dedicated PostgreSQL instance (`payment_db`).
- **Cache:** Redis — caches payment method lookups, idempotency keys.
- **Downstream:** Calls third-party Payment Gateway (Stripe-like) via REST with Resilience4j circuit breaker.
- **Events published:** `payment.completed`, `payment.failed`, `payment.refunded` → Kafka topic `payment-events`.

### 2.3 Order Service
- **Role:** Manages order lifecycle — creation, confirmation, fulfillment tracking, cancellation.
- **Domain entities:** `Order`, `OrderItem`, `OrderStatus`, `DeliveryInfo`.
- **Database:** Dedicated PostgreSQL instance (`order_db`).
- **Events published:** `order.created`, `order.confirmed`, `order.cancelled` → Kafka topic `order-events`.
- **Events consumed:** `payment.completed` (from Payment Service) to confirm orders.

### 2.4 Ledger Service
- **Role:** Immutable, append-only financial ledger. Records every monetary movement as double-entry journal entries.
- **Domain entities:** `JournalEntry`, `Account`, `LedgerTransaction`.
- **Database:** Dedicated PostgreSQL instance (`ledger_db`), append-only schema (no UPDATEs, no DELETEs in normal operation).
- **Events consumed:** `payment.completed`, `payment.refunded` → creates corresponding journal entries.

### 2.5 Notification Service
- **Role:** Sends transactional emails, SMS, push notifications triggered by domain events.
- **Events consumed:** `order.confirmed`, `payment.completed`, `payment.failed`.
- **Downstream:** Email provider (SES-like), SMS provider (Twilio-like).
- **Stateless** aside from a delivery log table in a shared PostgreSQL instance.

### 2.6 Analytics Service
- **Role:** Consumes all Kafka topics for real-time aggregation dashboards and anomaly detection.
- **Not a focus of most chapters** — exists to show multi-consumer Kafka patterns.

---

## 3. Technology Stack

| Layer               | Technology                             | Version (baseline) |
|---------------------|----------------------------------------|--------------------|
| Language            | Java                                   | 21 LTS             |
| Framework           | Spring Boot                            | 3.3.x              |
| ORM                 | Hibernate (via Spring Data JPA)        | 6.5.x              |
| Connection Pool     | HikariCP (Spring Boot default)         | 5.1.x              |
| Primary Database    | PostgreSQL                             | 16.x               |
| Cache               | Redis (Lettuce driver)                 | 7.x                |
| Message Broker      | Apache Kafka                           | 3.7.x              |
| Secondary Broker    | RabbitMQ (used in specific chapters)   | 3.13.x             |
| API Gateway         | Spring Cloud Gateway                   | 4.1.x              |
| Service Discovery   | Spring Cloud Netflix Eureka            | 4.1.x              |
| Config Server       | Spring Cloud Config                    | 4.1.x              |
| Resilience          | Resilience4j                           | 2.2.x              |
| Schema Migration    | Flyway                                 | 10.x               |
| Security            | Spring Security + OAuth2 Resource Svr  | 6.3.x              |
| Observability       | Micrometer + Prometheus + Grafana      | 1.13.x / 2.x / 11  |
| Tracing             | OpenTelemetry (OTLP exporter)          | 1.38.x             |
| Containerization    | Docker                                 | 26.x               |
| Orchestration       | Kubernetes                             | 1.30.x             |
| Testing             | JUnit 5, Testcontainers, WireMock      | 5.10 / 1.19 / 3.6  |
| Build               | Maven (multi-module)                   | 3.9.x              |

---

## 4. Scale Assumptions

These numbers are reused across all chapters unless a specific chapter stress-tests a different scale.
All figures are **(illustrative)** — intended for teaching, not benchmarking claims.

| Parameter                           | Value                                      |
|-------------------------------------|--------------------------------------------|
| Active registered users             | ~50,000                                    |
| Peak concurrent users               | ~8,000                                     |
| Peak request rate (gateway)         | ~4,000 req/sec                             |
| Application instances per service   | 20 (Payment, Order), 10 (Ledger)           |
| Requests per instance at peak       | ~200 req/sec (Payment/Order)               |
| HikariCP pool size per instance     | 10 connections                             |
| Total DB connections (Payment Svc)  | 200 (20 instances × 10)                    |
| PostgreSQL max_connections          | 300 (comfortable headroom at ~200 active)  |
| PostgreSQL effective comfortable    | ~150 concurrent active queries             |
| Redis cluster nodes                 | 6 (3 primary + 3 replica)                  |
| Kafka brokers                       | 3                                          |
| Kafka partitions (payment-events)   | 12                                         |
| Kafka partitions (order-events)     | 12                                         |
| Average payment API latency (p50)   | (illustrative) ~120ms                      |
| Average payment API latency (p99)   | (illustrative) ~800ms                      |
| Downstream payment gateway latency  | (illustrative) ~300ms p50, ~1.5s p99       |
| Average DB query latency (p50)      | (illustrative) ~5ms                        |
| JVM heap per instance               | 2 GB (-Xmx2g)                              |
| GC algorithm                        | G1GC (JDK 21 default)                      |

---

## 5. Database Schema Overview (High-Level)

### 5.1 payment_db

```sql
-- Core tables (simplified; chapters add indexes, constraints, audit columns)
CREATE TABLE payment_intent (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id     UUID NOT NULL,
    amount_cents    BIGINT NOT NULL,
    currency        VARCHAR(3) NOT NULL DEFAULT 'USD',
    status          VARCHAR(20) NOT NULL DEFAULT 'CREATED',
    idempotency_key VARCHAR(64) UNIQUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         INTEGER NOT NULL DEFAULT 0   -- optimistic locking
);

CREATE TABLE charge (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_intent_id UUID NOT NULL REFERENCES payment_intent(id),
    gateway_charge_id VARCHAR(128),
    status            VARCHAR(20) NOT NULL,
    failure_reason    TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE refund (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    charge_id  UUID NOT NULL REFERENCES charge(id),
    amount_cents BIGINT NOT NULL,
    reason     TEXT,
    status     VARCHAR(20) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 5.2 order_db

```sql
CREATE TABLE orders (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    customer_id  UUID NOT NULL,
    total_cents  BIGINT NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    version      INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE order_item (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id    UUID NOT NULL REFERENCES orders(id),
    product_id  UUID NOT NULL,
    quantity    INTEGER NOT NULL,
    unit_price_cents BIGINT NOT NULL
);
```

### 5.3 ledger_db

```sql
CREATE TABLE journal_entry (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    transaction_ref UUID NOT NULL,      -- links debit + credit pair
    account_id      UUID NOT NULL,
    entry_type      VARCHAR(6) NOT NULL, -- 'DEBIT' or 'CREDIT'
    amount_cents    BIGINT NOT NULL,
    description     TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
    -- No updated_at: append-only by design
);

CREATE TABLE account (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_type VARCHAR(30) NOT NULL,  -- 'CUSTOMER', 'MERCHANT', 'PLATFORM_FEE', 'SETTLEMENT'
    owner_id     UUID NOT NULL,
    currency     VARCHAR(3) NOT NULL DEFAULT 'USD'
);
```

---

## 6. Failure Domains

Each failure domain maps to one or more chapters and represents a real category of production incidents.

| Failure Domain                  | Affected Services       | Example Incidents                                      |
|---------------------------------|-------------------------|--------------------------------------------------------|
| **Database saturation**         | Payment, Order, Ledger  | HikariCP pool exhaustion, long-running queries, lock contention |
| **Downstream timeout**          | Payment                 | Third-party gateway slow/down, cascading thread exhaustion |
| **Cache failure / inconsistency** | Payment               | Redis cluster partition, stale idempotency keys        |
| **Message broker lag / failure** | Order, Notification    | Kafka consumer lag, poison pill messages, rebalancing storms |
| **Thread pool exhaustion**      | All services            | @Async with unbounded queues, blocking in reactive gateway |
| **Memory pressure / OOM**       | All services            | Entity graph over-fetching, unbounded result sets, GC thrashing |
| **Concurrency / data races**    | Payment, Order          | Lost updates without optimistic locking, phantom reads |
| **Security breach**             | API Gateway, all svcs   | JWT misconfiguration, missing authZ checks, SQL injection |
| **Schema migration failure**    | All services            | Flyway checksum mismatch, non-backward-compatible DDL during rolling deploy |
| **Deployment incident**         | All services            | Liveness probe misconfiguration, PDB violation, config drift |

---

## 7. Infrastructure Defaults

These defaults are assumed unless a chapter explicitly overrides them.

### 7.1 HikariCP (per service instance)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
      idle-timeout: 300000        # 5 minutes
      max-lifetime: 1800000       # 30 minutes
      connection-timeout: 30000   # 30 seconds
      leak-detection-threshold: 60000  # 1 minute
```

### 7.2 JVM Flags (per pod)

```
-Xms1g -Xmx2g
-XX:+UseG1GC
-XX:MaxGCPauseMillis=200
-XX:+HeapDumpOnOutOfMemoryError
-XX:HeapDumpPath=/tmp/heapdump.hprof
-Djava.security.egd=file:/dev/./urandom
```

### 7.3 Kubernetes Pod Spec (Payment Service example)

```yaml
resources:
  requests:
    cpu: "500m"
    memory: "2Gi"
  limits:
    cpu: "2000m"
    memory: "2Gi"    # limits == requests for memory to avoid OOM-kill surprises
livenessProbe:
  httpGet:
    path: /actuator/health/liveness
    port: 8080
  initialDelaySeconds: 30
  periodSeconds: 10
  failureThreshold: 3
readinessProbe:
  httpGet:
    path: /actuator/health/readiness
    port: 8080
  initialDelaySeconds: 15
  periodSeconds: 5
  failureThreshold: 3
```

### 7.4 Logging

```yaml
logging:
  level:
    root: INFO
    com.finflow: DEBUG
    org.hibernate.SQL: DEBUG              # chapter-specific toggle
    org.hibernate.orm.jdbc.bind: TRACE    # chapter-specific toggle
    com.zaxxer.hikari: DEBUG              # chapter-specific toggle
  pattern:
    console: "%d{ISO8601} [%thread] %-5level %logger{36} - traceId=%X{traceId} spanId=%X{spanId} - %msg%n"
```

---

## 8. Kafka Topic Conventions

| Topic               | Key               | Value Schema         | Partitions | Retention |
|----------------------|-------------------|----------------------|------------|-----------|
| `payment-events`     | `paymentIntentId` | JSON (CloudEvents)   | 12         | 7 days    |
| `order-events`       | `orderId`         | JSON (CloudEvents)   | 12         | 7 days    |
| `notification-events`| `customerId`      | JSON (CloudEvents)   | 6          | 3 days    |
| `dlq-payment-events` | original key      | original + error meta| 3          | 30 days   |
| `dlq-order-events`   | original key      | original + error meta| 3          | 30 days   |

---

## 9. Cross-Cutting Concerns

| Concern               | Approach                                                            |
|------------------------|---------------------------------------------------------------------|
| **Idempotency**        | Client-generated `Idempotency-Key` header; stored in `payment_intent.idempotency_key` with UNIQUE constraint; Redis short-circuit for in-flight requests |
| **Distributed Tracing**| OpenTelemetry auto-instrumentation agent; `traceId`/`spanId` in MDC for log correlation |
| **Audit Trail**        | JPA `@EntityListeners` + `AuditingEntityListener`; `created_by`, `updated_by`, `created_at`, `updated_at` on all mutable entities |
| **Secret Management**  | Spring Cloud Config (encrypted values) or Kubernetes Secrets mounted as env vars |
| **Health Checks**      | Spring Boot Actuator: `/actuator/health/liveness`, `/actuator/health/readiness` with custom DB/Redis/Kafka health indicators |

---

## Changelog

| Date       | Chapter | Change Description                                  |
|------------|---------|-----------------------------------------------------|
| 2026-08-18 | —       | Initial architecture document created (bootstrap)   |
