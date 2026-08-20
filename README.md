# Spring Boot Production Mastery

> **Enterprise Production Engineering Handbook & Reference Codebase**
> A chapter-by-chapter curriculum teaching Spring Boot 3.x and Java 21 from the perspective of real production incidents: how large-scale systems fail, how to debug them under pressure, and how to engineer resilient architectures.

---

## 📌 Reference System: FinFlow Payment Platform

All chapters anchor to a single running production system — **FinFlow**, a distributed payment processing platform handling high concurrency (~4,000 req/sec peak, 20 service pods, PostgreSQL, Redis Cluster, Kafka, Resilience4j):

```
Clients ──► API Gateway (Spring Cloud Gateway)
                 │
      ┌──────────┴──────────┬──────────────────┐
      ▼                     ▼                  ▼
Payment Service        Order Service      Ledger Service
 (20 pods)              (20 pods)          (10 pods)
  ├── PostgreSQL (payment_db)  ├── PostgreSQL     └── PostgreSQL (append-only)
  ├── Redis (idempotency/cache)├── Kafka (order-events)
  └── Payment Gateway (Stripe) └── Notification Service
```

Full architectural topology, database schemas, failure domains, and infrastructure defaults are detailed in [**ARCHITECTURE.md**](ARCHITECTURE.md).

---

## 📊 Curriculum & Progress Tracker

**Progress:** `24 / 40` Chapters Completed

Detailed Table of Contents and status are maintained in [**docs/HANDBOOK.md**](docs/HANDBOOK.md).

| Part | Chapter | Topic | Status |
|:---|:---|:---|:---:|
| **Part I — Foundations** | [010](docs/chapters/010-core-java-for-spring.md) | Core Java for Spring — Reflection, Annotations, Generics, Records | ✅ Complete |
| | [020](docs/chapters/020-jvm-internals.md) | JVM Internals — Memory Model, GC, Class Loading, JIT, Thread Scheduling | ✅ Complete |
| **Part II — Spring Core** | [030](docs/chapters/030-spring-core-ioc-application-context.md) | Spring Core — IoC Container, BeanFactory vs ApplicationContext | ✅ Complete |
| | [040](docs/chapters/040-bean-lifecycle-and-scopes.md) | Bean Lifecycle & Scopes — Initialization, Destruction, Custom Scopes | ✅ Complete |
| | [050](docs/chapters/050-dependency-injection-deep-dive.md) | Dependency Injection — Constructor vs Field, Qualifiers, Circular Dependencies | ✅ Complete |
| | [060](docs/chapters/060-spring-boot-auto-configuration.md) | Auto-Configuration — Conditional Annotations, AutoConfiguration.imports, Starters | ✅ Complete |
| **Part III — Web Layer** | [070](docs/chapters/070-spring-mvc-request-lifecycle.md) | Spring MVC Request Lifecycle — DispatcherServlet, Interceptors, Filters | ✅ Complete |
| | [080](docs/chapters/080-validation.md) | Validation — Bean Validation, Custom Validators, Validation Groups | ✅ Complete |
| | [090](docs/chapters/090-exception-handling.md) | Exception Handling — @ControllerAdvice, ProblemDetail (RFC 9457) | ✅ Complete |
| | [100](docs/chapters/100-jackson-serialization.md) | Jackson — Serialization, Custom Serializers, Mixins, Views, Pitfalls | ✅ Complete |
| **Part IV — AOP & Proxies** | [110](docs/chapters/110-spring-aop-and-proxy-mechanism.md) | Spring AOP & Proxies — JDK Dynamic Proxy vs CGLIB, Self-Invocation | ✅ Complete |
| **Part V — Data Access** | [120](docs/chapters/120-spring-data-jpa-fundamentals.md) | Spring Data JPA — Repository Abstraction, Query Derivation, Projections | ✅ Complete |
| | [130](docs/chapters/130-hibernate-internals-entity-lifecycle.md) | Hibernate Internals & Entity Lifecycle — Session, Entity States | ✅ Complete |
| | [140](docs/chapters/140-persistence-context-flush-dirty-checking.md) | Persistence Context — Dirty Checking, Flush Modes, Write-Behind | ✅ Complete |
| | [150](docs/chapters/150-lazy-loading-and-entity-graphs.md) | Lazy Loading & Entity Graphs — N+1 Problem, JOIN FETCH, Batch Fetching | ✅ Complete |
| | [160](docs/chapters/160-batch-processing.md) | Batch Processing — JDBC Batching, Hibernate Batching, Bulk Operations | ✅ Complete |
| **Part VI — Transactions** | [170](docs/chapters/170-transactions-propagation-isolation.md) | Transactions — @Transactional Internals, Propagation, Isolation Levels | ✅ Complete |
| | [180](docs/chapters/180-optimistic-pessimistic-locking.md) | Locking — @Version Optimistic Locking, SELECT FOR UPDATE, Deadlocks | ✅ Complete |
| **Part VII — Databases** | [190](docs/chapters/190-hikaricp-connection-pool.md) | HikariCP Deep Dive — Pool Sizing, Leak Detection, Metrics Lifecycle | ✅ Complete |
| | [200](docs/chapters/200-postgresql-mysql-for-spring.md) | PostgreSQL & MySQL — Engine Differences, Indexes, Execution Plans, MVCC | ✅ Complete |
| | [210](docs/chapters/210-flyway-liquibase-migrations.md) | Database Migrations — Flyway & Liquibase, Zero-Downtime DDL | ✅ Complete |
| **Part VIII — Security** | [220](docs/chapters/220-spring-security-fundamentals.md) | Spring Security — Filter Chain Architecture, SecurityContext, Method Security | ✅ Complete |
| | [230](docs/chapters/230-jwt-authentication.md) | JWT Authentication — Token Lifecycle, Refresh Tokens, Key Rotation | ✅ Complete |
| | [240](docs/chapters/240-oauth2-openid-connect.md) | OAuth2 & OpenID Connect — Authorization Server, Resource Server | ✅ Complete |
| **Part IX — Caching** | [250](docs/chapters/250-redis-spring-cache.md) | Redis & Spring Cache — @Cacheable Internals, Stampede Prevention, Failover | ⬜ Not Started |
| **Part X — Messaging** | [260](docs/chapters/260-kafka-with-spring-boot.md) | Kafka — Producer/Consumer Internals, Consumer Groups, Rebalancing, DLQ | ⬜ Not Started |
| | [270](docs/chapters/270-rabbitmq-with-spring-boot.md) | RabbitMQ — Exchanges, Queues, ACKs, Dead Letter Exchanges | ⬜ Not Started |
| **Part XI — Async** | [280](docs/chapters/280-scheduling-cron-jobs.md) | Scheduling — @Scheduled, ShedLock Distributed Locking, Leader Election | ⬜ Not Started |
| | [290](docs/chapters/290-async-processing-thread-pools.md) | Async & Thread Pools — ThreadPoolTaskExecutor, Backpressure, Queues | ⬜ Not Started |
| **Part XII — Containers** | [300](docs/chapters/300-docker-for-spring-boot.md) | Docker — Multi-Stage Builds, Layered JARs, cgroup Limits, Distroless | ⬜ Not Started |
| | [310](docs/chapters/310-kubernetes-for-spring-boot.md) | Kubernetes — Probes, Graceful Shutdown, PDB, HPA, Rolling Deployments | ⬜ Not Started |
| **Part XIII — Metrics** | [320](docs/chapters/320-micrometer-prometheus-grafana.md) | Micrometer, Prometheus & Grafana — Custom Metrics, Dashboards, Alerting | ⬜ Not Started |
| | [330](docs/chapters/330-opentelemetry-distributed-tracing.md) | OpenTelemetry & Distributed Tracing — W3C Propagation, MDC Correlation | ⬜ Not Started |
| **Part XIV — Resilience** | [340](docs/chapters/340-resilience4j-circuit-breaker-rate-limiting.md) | Resilience4j — Circuit Breaker, Retry, Rate Limiter, Bulkhead | ⬜ Not Started |
| | [350](docs/chapters/350-api-gateway-spring-cloud-gateway.md) | API Gateway — Spring Cloud Gateway, Route Predicates, Filters, Rate Limits | ⬜ Not Started |
| | [360](docs/chapters/360-spring-cloud-config-eureka.md) | Spring Cloud Config & Service Discovery — Config Server, Eureka | ⬜ Not Started |
| **Part XV — Distributed** | [370](docs/chapters/370-distributed-transactions.md) | Distributed Transactions — Saga Pattern, Outbox Pattern, Choreography | ⬜ Not Started |
| **Part XVI — Production** | [380](docs/chapters/380-observability-in-production.md) | Observability in Production — Log Aggregation, Runbooks, On-Call Workflows | ⬜ Not Started |
| | [390](docs/chapters/390-performance-tuning.md) | Performance Tuning — Profiling, JFR, Flame Graphs, Query Tuning, GC Tuning | ⬜ Not Started |
| | [400](docs/chapters/400-production-deployment.md) | Production Deployment — CI/CD Pipelines, Blue-Green, Canary, Feature Flags | ⬜ Not Started |

---

## 🏗️ Chapter Standard & Structure

Every single chapter adheres strictly to **16 mandatory production engineering sections**:

1. **Concept** — Core problem statement and architectural motivation
2. **Internal Working** — Deep dive into framework SPIs, bytecode manipulation, and runtime mechanics
3. **Enterprise Scenario** — Incident backdrop in the FinFlow system under real traffic
4. **Incorrect Implementation** — Flawed, anti-pattern code that triggers failure in production
5. **Production Incident** — Incident timeline, alerting, customer blast radius, and SRE impact
6. **Logs** — Realistic logs and stack traces from App, Spring, Hibernate, HikariCP, or Kubernetes
7. **Root Cause Analysis** — Exact internal mechanisms and thread/memory dynamics
8. **Debugging Process** — Step-by-step on-call engineer triage workflow
9. **Correct Implementation** — Hardened, production-ready code with complete rationale
10. **Performance Comparison** — Before/after metrics labeled `(illustrative)`
11. **Best Practices** — Justified Do's and Don'ts checklist
12. **Common Mistakes** — Subtle bugs and anti-patterns encountered in the wild
13. **Interview Questions** — Tiered questions from Junior to Principal level
14. **Hands-on Exercise** — Practical scenario with expected solution
15. **Advanced Challenge** — Enterprise-scale stretch problem
16. **Production Checklist** — Pull-request review gate criteria

---

## 🛠️ Tech Stack & Baseline

- **Language:** Java 21 LTS
- **Framework:** Spring Boot 3.3.x
- **ORM / Data Access:** Hibernate 6.5.x, Spring Data JPA, HikariCP
- **Databases & Cache:** PostgreSQL 16, Redis 7
- **Messaging:** Apache Kafka 3.7, RabbitMQ 3.13
- **Resilience & Observability:** Resilience4j, Micrometer, Prometheus, OpenTelemetry
- **Testing:** JUnit 5, Spring Boot Test, Testcontainers, H2

---

## 🚀 Running Code & Tests

Each chapter has a runnable Maven module under `code/chapter-NNN/`.

To build and run tests for any chapter:

```bash
cd code/chapter-060
mvn clean test
```
