# 🛠️ Spring Boot Production Troubleshooting & Debugging Master Guide

Welcome to the **Spring Boot Production Troubleshooting, Root Cause Analysis (RCA), and Debugging Master Program**.

This guide is an exhaustive, production-grade engineering laboratory designed to train software engineers, tech leads, and SREs to investigate, diagnose, and resolve real-world incidents across the entire application and infrastructure stack:

```text
Client ──► DNS ──► CDN / Load Balancer ──► Nginx / Ingress ──► API Gateway
                                                                   │
    ┌──────────────────────────────────────────────────────────────┘
    ▼
Spring Boot Application
    ├── Spring Security (Authentication, Authorization, Filters)
    ├── Spring MVC / Web (DispatcherServlet, Controllers, Jackson)
    ├── Spring AOP & Proxies (@Transactional, @Async, @Cacheable)
    ├── Domain Services & Concurrency Engine (Thread Pools, CAS, Locking)
    ├── Spring Data JPA & Hibernate (Session, Persistence Context, Dirty Checking)
    └── HikariCP Connection Pool
            │
            ├──► Relational Databases (PostgreSQL, MySQL)
            ├──► Distributed Caches (Redis Cluster)
            ├──► Event Streaming & Message Brokers (Kafka, RabbitMQ)
            └──► Downstream Microservices & 3rd-Party APIs
```

---

## 🧭 Master Curriculum & 28-Module Roadmap

| Module | Topic | Domain | Difficulty | Status |
|:---|:---|:---|:---:|:---:|
| **[00](docs/00-program-architecture.md)** | **Program Architecture & Tooling Matrix** | Foundation | All Levels | ✅ Complete |
| **[01](docs/module-01-startup-failures.md)** | **Startup & ApplicationContext Failures** | Spring Core / IoC | Intermediate | ✅ Complete |
| **[02](docs/module-02-configuration-problems.md)** | **Configuration & Environment Drift** | Config / Cloud / K8s | Intermediate | ✅ Complete |
| **[03](docs/module-03-dependency-problems.md)** | **Maven, Gradle, Java & Dependency Conflicts** | Build & JVM Runtime | Intermediate | ✅ Complete |
| **[04](docs/module-04-rest-mvc-http-problems.md)** | **REST, MVC, HTTP & API Network Errors (4xx/5xx)** | Web Layer / Gateway | Intermediate | ✅ Complete |
| **[05](docs/module-05-validation-exception-handling.md)** | **Validation & Global Exception Handling** | Web / Security | Intermediate | ✅ Complete |
| **[06](docs/module-06-aop-proxy-problems.md)** | **Spring AOP & Proxy Traps (Self-Invocation, Aspects)** | Spring Core / AOP | Advanced | ✅ Complete |
| **[07](docs/module-07-spring-transactions.md)** | **Spring Transactions & Isolation Hazards** | Data & Tx Engine | Advanced | ✅ Complete |
| **[08](docs/module-08-jpa-hibernate-bottlenecks.md)** | **JPA & Hibernate Production Bottlenecks** | ORM / Persistence | Advanced | ✅ Complete |
| **[09](docs/module-09-hikaricp-connection-pool-exhaustion.md)** | **Database & HikariCP Connection Pool Exhaustion** | Infrastructure / DB | Advanced | ✅ Complete |
| **[10](docs/module-10-database-performance-query-plans-deadlocks.md)** | **Database Performance, Query Plans & Deadlocks** | Database Internals | Expert | ✅ Complete |
| **[11](docs/module-11-spring-security-jwt-filter-chain.md)** | **Spring Security, JWT & Filter Chain Breakages** | Security / Auth | Advanced | ✅ Complete |
| **[12](docs/module-12-external-api-timeouts-circuit-breakers.md)** | **External API Timeouts, Retries & Cascading Cascades** | Distributed Systems | Advanced | ✅ Complete |
| **[13](docs/module-13-jvm-threads-async-pool-saturation.md)** | **JVM Threads, Async & Thread Pool Saturation** | Concurrency / JVM | Expert | ✅ Complete |
| **[14](docs/module-14-jvm-memory-leaks-metaspace-gc-oom.md)** | **JVM Memory Leaks, Metaspace, GC Thrashing & OOM** | JVM Performance | Expert | ✅ Complete |
| **15** | **Logging, Observability, MDC & Tracing Gaps** | Observability / SRE | Advanced | ⬜ Planned |
| **16** | **Docker Containerization & Cgroup Traps** | Containers | Intermediate | ⬜ Planned |
| **17** | **Kubernetes Pod Lifecycle, Probes & OOMKilled** | Orchestration / K8s | Advanced | ⬜ Planned |
| **18** | **API Gateway, Nginx & Reverse Proxy Timeouts** | Edge / Networking | Advanced | ⬜ Planned |
| **19** | **Redis Caching: Stampede, Avalanche & Invalidation** | Distributed Cache | Advanced | ⬜ Planned |
| **20** | **Apache Kafka: Consumer Lag, Poison Pills & Rebalances** | Messaging / Kafka | Expert | ⬜ Planned |
| **21** | **Concurrency, Race Conditions & Distributed Locks** | Concurrency / Data | Expert | ⬜ Planned |
| **22** | **Scheduled Jobs, Job Overlaps & Cluster Duplication** | Scheduling / Async | Intermediate | ⬜ Planned |
| **23** | **File Uploads, Storage Leaks & Ephemeral Containers** | Web & Storage | Intermediate | ⬜ Planned |
| **24** | **Timezones, DST, Instant vs LocalDateTime & Clock Skew**| Core Runtime | Intermediate | ⬜ Planned |
| **25** | **Database Migrations: Flyway, Locks & Zero-Downtime** | Database DevOps | Advanced | ⬜ Planned |
| **26** | **Deployment Failures: Rolling, Blue-Green & Canary** | Release Engineering | Advanced | ⬜ Planned |
| **27** | **Distributed Microservice Failure & Sagas** | Distributed Systems | Expert | ⬜ Planned |
| **28** | **Production Incident Response (20 Comprehensive Scenarios)**| Incident Command | Expert | ⬜ Planned |

---

## 🔬 Universal Production Incident Debugging Framework

Every production incident follows a strict 14-step scientific protocol:

```text
 1. DETECT           ──► Alert fires / user ticket received with timestamp & region.
 2. DEFINE SYMPTOM   ──► Formulate precise observable symptom (e.g. "P99 latency spiked 200ms -> 4.2s").
 3. BLAST RADIUS     ──► Identify impacted users, tenants, endpoints, and microservices.
 4. RECENT CHANGES   ──► Audit deployments, feature flag toggles, config drift, and DB migrations.
 5. FAILING LAYER    ──► Isolate the layer (DNS, LB, Gateway, App, DB, Cache, Network).
 6. EVIDENCE         ──► Gather logs, metrics, traces, thread dumps, heap dumps, query plans.
 7. HYPOTHESIZE      ──► Formulate testable root cause hypotheses.
 8. ELIMINATE        ──► Disprove invalid hypotheses using gathered telemetry.
 9. ROOT CAUSE       ──► Pinpoint exact code line, lock, query, or kernel parameter.
10. MITIGATE SAFELY  ──► Contain the bleeding (traffic shift, kill switch, rollback, scale).
11. PERMANENT FIX    ──► Implement robust, tested code/infrastructure patch.
12. VERIFY           ──► Confirm metric recovery under simulated or live traffic.
13. MONITOR          ──► Set high-resolution watch for secondary regression.
14. RCA & RUNBOOK    ──► Publish blameless Root Cause Analysis and automated prevention alerts.
```

---

## 🧰 Production Tooling Matrix

- **JVM Diagnostics:** `jcmd`, `jstack`, `jmap`, `jstat`, JDK Flight Recorder (`JFR`), JDK Mission Control (`JMC`), `async-profiler`, Eclipse Memory Analyzer Tool (`MAT`).
- **Database Profiling:** `EXPLAIN (ANALYZE, BUFFERS)`, `pg_stat_activity`, `pg_stat_statements`, MySQL `SHOW ENGINE INNODB STATUS`, HikariCP Actuator Metrics.
- **Linux & Kernel:** `strace`, `lsof`, `tcpdump`, `netstat`/`ss`, `vmstat`, `iostat`, `top`/`htop`, `perf`.
- **Containers & K8s:** `kubectl describe`, `kubectl logs`, `kubectl top`, cgroup metrics (`/sys/fs/cgroup`).
- **Observability:** Prometheus, Grafana, OpenTelemetry, Jaeger, Tempo, Grafana Loki, Elasticsearch.
