# Spring Boot Production Mastery — Handbook

> **Enterprise Production Engineering Handbook**
> A chapter-by-chapter curriculum teaching Spring Boot from the perspective of real production
> incidents: how systems fail, how to debug them, and how to design robust solutions.

**Reference Architecture:** [ARCHITECTURE.md](../ARCHITECTURE.md)
**Spring Boot:** 3.3.x &nbsp;|&nbsp; **Java:** 21 LTS &nbsp;|&nbsp; **Hibernate:** 6.5.x

---

## Table of Contents

### Part I — Foundations (Java & JVM for Spring Engineers)

| Chapter | Topic | Status |
|---------|-------|--------|
| [010](chapters/010-core-java-for-spring.md) | Core Java for Spring — Reflection, Annotations, Generics, Functional Interfaces, Records | ✅ Complete |
| [020](chapters/020-jvm-internals.md) | JVM Internals — Memory Model, GC, Class Loading, JIT, Thread Scheduling | ✅ Complete |

### Part II — Spring Core

| Chapter | Topic | Status |
|---------|-------|--------|
| [030](chapters/030-spring-core-ioc-application-context.md) | Spring Core — IoC Container, BeanFactory vs ApplicationContext | ✅ Complete |
| [040](chapters/040-bean-lifecycle-and-scopes.md) | Bean Lifecycle & Scopes — Initialization, Destruction, Singleton, Prototype, Request, Session | ✅ Complete |
| [050](chapters/050-dependency-injection-deep-dive.md) | Dependency Injection Deep Dive — Constructor vs Field vs Setter, Qualifiers, Circular Dependencies | ✅ Complete |
| [060](chapters/060-spring-boot-auto-configuration.md) | Spring Boot Auto-Configuration — Conditional Annotations, spring.factories, META-INF, Custom Starters | ✅ Complete |

### Part III — Web Layer

| Chapter | Topic | Status |
|---------|-------|--------|
| [070](chapters/070-spring-mvc-request-lifecycle.md) | Spring MVC Request Lifecycle — DispatcherServlet, HandlerMapping, Interceptors, Argument Resolvers | ✅ Complete |
| [080](chapters/080-validation.md) | Validation — Bean Validation, Custom Validators, Validation Groups, Error Response Contracts | ✅ Complete |
| [090](chapters/090-exception-handling.md) | Exception Handling — @ControllerAdvice, ProblemDetail (RFC 9457), Error Hierarchy Design | ✅ Complete |
| [100](chapters/100-jackson-serialization.md) | Jackson — Serialization, Deserialization, Custom Serializers, Mixins, Views, Production Pitfalls | ✅ Complete |

### Part IV — AOP & Proxies

| Chapter | Topic | Status |
|---------|-------|--------|
| [110](chapters/110-spring-aop-and-proxy-mechanism.md) | Spring AOP & Proxy Mechanism — JDK Dynamic Proxy vs CGLIB, Proxy Pitfalls, Self-Invocation | ✅ Complete |

### Part V — Data Access & Hibernate

| Chapter | Topic | Status |
|---------|-------|--------|
| [120](chapters/120-spring-data-jpa-fundamentals.md) | Spring Data JPA — Repository Abstraction, Query Derivation, @Query, Projections, Specifications | ✅ Complete |
| [130](chapters/130-hibernate-internals-entity-lifecycle.md) | Hibernate Internals & Entity Lifecycle — SessionFactory, Session, Entity States (Transient, Managed, Detached, Removed) | ✅ Complete |
| [140](chapters/140-persistence-context-flush-dirty-checking.md) | Persistence Context — Dirty Checking, Flush Modes, Write-Behind, ActionQueue | ✅ Complete |
| [150](chapters/150-lazy-loading-and-entity-graphs.md) | Lazy Loading & Entity Graphs — N+1 Problem, JOIN FETCH, @EntityGraph, Batch Fetching | ✅ Complete |
| [160](chapters/160-batch-processing.md) | Batch Processing — JDBC Batching, Hibernate Batching, Bulk Operations, Chunk Processing | ✅ Complete |

### Part VI — Transactions & Concurrency

| Chapter | Topic | Status |
|---------|-------|--------|
| [170](chapters/170-transactions-propagation-isolation.md) | Transactions — @Transactional Internals, Propagation Behaviors, Isolation Levels | ✅ Complete |
| [180](chapters/180-optimistic-pessimistic-locking.md) | Optimistic & Pessimistic Locking — @Version, SELECT FOR UPDATE, Deadlock Prevention | ✅ Complete |

### Part VII — Connection Management & Databases

| Chapter | Topic | Status |
|---------|-------|--------|
| [190](chapters/190-hikaricp-connection-pool.md) | HikariCP Deep Dive — Pool Sizing, Leak Detection, Metrics, Connection Lifecycle | ✅ Complete |
| [200](chapters/200-postgresql-mysql-for-spring.md) | PostgreSQL & MySQL — Engine Differences, Index Strategy, Query Plans, MVCC, Vacuum | ✅ Complete |
| [210](chapters/210-flyway-liquibase-migrations.md) | Database Migrations — Flyway & Liquibase, Zero-Downtime DDL, Rollback Strategy | ✅ Complete |

### Part VIII — Security

| Chapter | Topic | Status |
|---------|-------|--------|
| [220](chapters/220-spring-security-fundamentals.md) | Spring Security — Filter Chain, Authentication Architecture, SecurityContext, Method Security | ✅ Complete |
| [230](chapters/230-jwt-authentication.md) | JWT Authentication — Token Lifecycle, Refresh Tokens, Key Rotation, Common Vulnerabilities | ✅ Complete |
| [240](chapters/240-oauth2-openid-connect.md) | OAuth2 & OpenID Connect — Authorization Server, Resource Server, Token Introspection | ✅ Complete |

### Part IX — Caching

| Chapter | Topic | Status |
|---------|-------|--------|
| [250](chapters/250-redis-spring-cache.md) | Redis & Spring Cache — @Cacheable Internals, Eviction, Stampede Prevention, Cluster Failover | ✅ Complete |

### Part X — Messaging

| Chapter | Topic | Status |
|---------|-------|--------|
| [260](chapters/260-kafka-with-spring-boot.md) | Kafka — Producer/Consumer Internals, Exactly-Once, Consumer Groups, Rebalancing, DLQ | ✅ Complete |
| [270](chapters/270-rabbitmq-with-spring-boot.md) | RabbitMQ — Exchanges, Queues, Acknowledgments, Dead Letter Exchanges, Retry Patterns | ✅ Complete |

### Part XI — Async & Scheduling

| Chapter | Topic | Status |
|---------|-------|--------|
| [280](chapters/280-scheduling-cron-jobs.md) | Scheduling — @Scheduled, Cron Expressions, Distributed Locking (ShedLock), Leader Election | ✅ Complete |
| [290](chapters/290-async-processing-thread-pools.md) | Async Processing & Thread Pools — @Async, CompletableFuture, ThreadPoolTaskExecutor, Backpressure | ✅ Complete |

### Part XII — Containerization & Orchestration

| Chapter | Topic | Status |
|---------|-------|--------|
| [300](chapters/300-docker-for-spring-boot.md) | Docker — Multi-Stage Builds, Layered JARs, JVM in Containers, cgroup Limits, Distroless Images | ✅ Complete |
| [310](chapters/310-kubernetes-for-spring-boot.md) | Kubernetes — Probes, Resource Limits, ConfigMaps/Secrets, Rolling Updates, PDB, HPA, Graceful Shutdown | ✅ Complete |

### Part XIII — Observability

| Chapter | Topic | Status |
|---------|-------|--------|
| [320](chapters/320-micrometer-prometheus-grafana.md) | Micrometer, Prometheus & Grafana — Custom Metrics, Dashboards, Alerting, SLOs | ✅ Complete |
| [330](chapters/330-opentelemetry-distributed-tracing.md) | OpenTelemetry & Distributed Tracing — Auto-instrumentation, Span Propagation, Trace-Log Correlation | ⬜ Not Started |

### Part XIV — Resilience & Traffic Management

| Chapter | Topic | Status |
|---------|-------|--------|
| [340](chapters/340-resilience4j-circuit-breaker-rate-limiting.md) | Resilience4j — Circuit Breaker, Retry, Rate Limiter, Bulkhead, TimeLimiter | ⬜ Not Started |
| [350](chapters/350-api-gateway-spring-cloud-gateway.md) | API Gateway — Spring Cloud Gateway, Route Predicates, Filters, Rate Limiting at the Edge | ⬜ Not Started |
| [360](chapters/360-spring-cloud-config-eureka.md) | Spring Cloud Config & Service Discovery — Config Server, Eureka, Client-Side Load Balancing | ⬜ Not Started |

### Part XV — Distributed Systems

| Chapter | Topic | Status |
|---------|-------|--------|
| [370](chapters/370-distributed-transactions.md) | Distributed Transactions — Saga Pattern, Transactional Outbox, Choreography vs Orchestration | ⬜ Not Started |

### Part XVI — Production Engineering

| Chapter | Topic | Status |
|---------|-------|--------|
| [380](chapters/380-observability-in-production.md) | Observability in Production — Log Aggregation, Alerting Pipelines, Runbooks, On-Call Workflows | ⬜ Not Started |
| [390](chapters/390-performance-tuning.md) | Performance Tuning — Profiling, JFR, Flame Graphs, Query Optimization, Connection Tuning, GC Tuning | ⬜ Not Started |
| [400](chapters/400-production-deployment.md) | Production Deployment — CI/CD, Blue-Green, Canary, Feature Flags, Release Engineering | ⬜ Not Started |

---

## Chapter Count: 40

## Progress: 32 / 40 chapters completed

---

## Conventions

- **Chapter numbering:** Increments of 10 (`010`, `020`, ...) to allow insertion of new chapters without renumbering.
- **Chapter files:** `docs/chapters/NNN-topic-slug.md`
- **Code samples:** `code/chapter-NNN/` (runnable Maven module or package)
- **Each chapter contains all 16 mandatory sections** as defined in the master prompt.
- **Illustrative numbers** are prefixed with `(illustrative)` per Operating Rule 5.
