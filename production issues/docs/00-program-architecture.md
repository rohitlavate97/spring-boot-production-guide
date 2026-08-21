# Phase 0: Master Program Architecture & Debugging Laboratory Roadmap

## 1. Executive Summary & Objective

The **Spring Boot Production Troubleshooting and Debugging Master Guide** is engineered to transform developers and engineers into elite production troubleshooters. It bridges the gap between theoretical software development and real-world high-stakes incident response, equipping engineers with the mental models, evidence-gathering disciplines, and diagnostic tooling required to solve complex issues across modern distributed architectures.

---

## 2. Complete 28-Module Curriculum & Dependency Graph

```
┌──────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                 MODULE LEARNING DEPENDENCY GRAPH                                 │
│                                                                                                  │
│   [Core Mechanics]          Module 01 (Startup) ──► Module 02 (Config) ──► Module 03 (Deps)      │
│                                      │                                                           │
│                                      ▼                                                           │
│   [Web & Proxies]           Module 04 (HTTP/MVC) ──► Module 05 (Validation) ──► Module 06 (AOP)  │
│                                                                                     │            │
│                                                                                     ▼            │
│   [Data & Transactions]     Module 07 (Tx Engine) ◄──► Module 08 (Hibernate/JPA)                 │
│                                      │                                                           │
│                                      ▼                                                           │
│   [Database & Locks]        Module 09 (HikariCP)  ──► Module 10 (Deadlocks & SQL Plans)          │
│                                                                                     │            │
│                                                                                     ▼            │
│   [Security & Systems]      Module 11 (Security)  ──► Module 12 (API Timeouts)                   │
│                                                                                     │            │
│                                                                                     ▼            │
│   [JVM & Performance]       Module 13 (Threads)   ──► Module 14 (GC & Memory) ──► Module 15 (Obs)│
│                                                                                     │            │
│                                                                                     ▼            │
│   [Infra & Orchestration]   Module 16 (Docker)    ──► Module 17 (K8s/Probes)  ──► Module 18 (GW) │
│                                                                                     │            │
│                                                                                     ▼            │
│   [Distributed State]       Module 19 (Redis)     ──► Module 20 (Kafka/MQ)    ──► Module 21 (Race│
│                                                                                      Conditions) │
│                                                                                     │            │
│                                                                                     ▼            │
│   [Operations & Deploy]     Module 22-26 (Jobs, Storage, Timezones, Flyway, Canary Release)      │
│                                                                                     │            │
│                                                                                     ▼            │
│   [Mastery & Incident Response] Module 27 (Distributed Systems) ──► Module 28 (Incident Command)│
└──────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 3. Module Difficulty & Prerequisites Matrix

| Module | Title | Difficulty | Target Domain | Prerequisites |
|:---|:---|:---:|:---|:---|
| **01** | Startup & ApplicationContext Failures | Intermediate | Spring Core, IoC | Java 21, Spring Basics |
| **02** | Configuration & Environment Drift | Intermediate | Cloud / Config | Spring Environment |
| **03** | Maven, Gradle & Dependency Conflicts | Intermediate | Build / ClassLoader | Maven / JAR structure |
| **04** | REST, MVC, HTTP & 4xx/5xx API Errors | Intermediate | Web / HTTP | HTTP Protocol, MVC |
| **05** | Validation & Global Exception Handling | Intermediate | Web / API Design | Jakarta Validation |
| **06** | Spring AOP & Dynamic Proxy Pitfalls | Advanced | Spring Core / AOP | JDK Proxy vs CGLIB |
| **07** | Spring Transactions & Isolation Hazards | Advanced | Data / ACID | Database Transactions |
| **08** | JPA & Hibernate Production Bottlenecks | Advanced | ORM / Persistence | SQL, JPA, Hibernate |
| **09** | Database & HikariCP Pool Exhaustion | Advanced | Infrastructure / DB | Connection Pools |
| **10** | Database Performance, Query Plans & Deadlocks | Expert | Database Internals | PostgreSQL/MySQL MVCC |
| **11** | Spring Security & JWT Filter Chain Breakages | Advanced | Security / Auth | Security Filter Chains |
| **12** | External API Timeouts & Cascading Failures | Advanced | Distributed Systems | HTTP Client, Circuit Breakers |
| **13** | JVM Threads, Async & Pool Saturation | Expert | Concurrency / JVM | Java Concurrency, Threads |
| **14** | JVM Memory Leaks, GC Thrashing & OOM | Expert | JVM Performance | JVM Memory Model, GC |
| **15** | Logging, Observability, MDC & Tracing | Advanced | SRE / Observability | Prometheus, OpenTelemetry |
| **16** | Docker Containerization & Cgroup Limits | Intermediate | Containers | Docker, Linux cgroups |
| **17** | Kubernetes Pod Lifecycle, Probes & OOMKilled | Advanced | Orchestration | Kubernetes, Pod Probes |
| **18** | API Gateway, Nginx & Proxy Timeouts | Advanced | Edge / Networking | Nginx, Reverse Proxies |
| **19** | Redis Caching: Stampede, Avalanche & Expiry | Advanced | Caching | Redis, Memory eviction |
| **20** | Apache Kafka: Lag, Poison Pills & Rebalance | Expert | Event Streaming | Kafka Consumer Groups |
| **21** | Concurrency, Race Conditions & Distributed Locks | Expert | Concurrency / Data | Distributed Locking |
| **22** | Scheduled Jobs & Cluster Duplication | Intermediate | Async / Scheduling | ShedLock, Quartz |
| **23** | File Uploads, Memory Leaks & Ephemeral Disks | Intermediate | Storage | Multipart, Object Storage |
| **24** | Timezones, DST, Instant vs LocalDateTime | Intermediate | Core Runtime | ISO-8601, Epoch Time |
| **25** | Database Migrations & Zero-Downtime DDL | Advanced | Database DevOps | Flyway, Expand-Contract |
| **26** | Deployment Failures: Rolling, Blue-Green & Canary | Advanced | Release Engineering | CI/CD, Istio, Flagger |
| **27** | Distributed Microservice Failure & Sagas | Expert | Distributed Systems | Saga, Outbox Pattern |
| **28** | Production Incident Response (20 Scenarios) | Expert | Incident Command | All Previous Modules |

---

## 4. Production Diagnostic Toolchain Matrix

```
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                                 PRODUCTION DIAGNOSTIC TOOLCHAIN                                 │
│                                                                                                 │
│  🔧 JVM & THREADS         ──► jcmd, jstack, jmap, jstat, JFR (JDK Flight Recorder),            │
│                               async-profiler, Eclipse MAT (Memory Analyzer Tool)                │
│                                                                                                 │
│  🗄️ DATABASE & QUERIES     ──► EXPLAIN (ANALYZE, BUFFERS), pg_stat_activity,                    │
│                               pg_stat_statements, MySQL Slow Query Log, HikariCP Metrics        │
│                                                                                                 │
│  🌐 NETWORK & PACKETS     ──► tcpdump, Wireshark, netstat, ss, curl, dig, traceroute, iptables │
│                                                                                                 │
│  🐧 LINUX OS & KERNEL     ──► strace, lsof, vmstat, iostat, mpstat, top/htop, /proc & /sys      │
│                                                                                                 │
│  ☸️ KUBERNETES & CLOUD    ──► kubectl describe, kubectl logs, kubectl top, cgroup telemetry    │
│                                                                                                 │
│  📊 OBSERVABILITY SUITE   ──► Prometheus, Grafana, OpenTelemetry (OTel), Jaeger, Grafana Loki  │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 5. End-to-End Request Path Layer Isolation Model

When debugging production incidents, always locate the exact failing layer along the request execution path:

```text
Layer 0: Client & DNS                (DNS resolution, TCP SYN timeouts, client-side aborts)
Layer 1: Edge & CDN                  (Cloudflare/CloudFront caching, SSL negotiation, edge rate limits)
Layer 2: Ingress & Reverse Proxy     (Nginx/HAProxy buffer sizes, 502/504 proxy timeouts)
Layer 3: API Gateway                 (Spring Cloud Gateway filter chain, token validation, edge routing)
Layer 4: Spring Security Filter Chain(SecurityContextHolder, JWT decoding, CORS/CSRF filters, 401/403)
Layer 5: Spring MVC Web Layer        (DispatcherServlet, HandlerMapping, Jackson Deserialization, 400/405/415)
Layer 6: Spring AOP & Proxies        (JDK Dynamic / CGLIB interception, @Transactional boundaries, @Async)
Layer 7: Domain Service Engine       (Business invariants, thread pool executors, lock contention)
Layer 8: Persistence Context / JPA   (Hibernate Session, 1st level cache, dirty checking, lazy loading)
Layer 9: Connection Pool (HikariCP)  (Active connections, queue wait time, leak detection timeouts)
Layer 10: Infrastructure / DB / MQ   (PostgreSQL row locks, Redis memory, Kafka consumer lag, 3rd party APIs)
```

---

## 6. How to Use This Interactive Guide

Each module is structured in an interactive, hands-on learning cycle:
- **Part A — Concept & Internals:** Deep technical exploration of framework and runtime mechanics.
- **Part B — Production Scenario:** A realistic high-stakes incident.
- **Part C — Investigation Challenge:** You are placed on-call and asked: *"What would you check first and why?"*
- **Part D — Simulated Telemetry & Evidence:** Real logs, metrics, thread dumps, or stack traces provided.
- **Part E — Your Analysis:** Interactive evaluation where you formulate hypotheses.
- **Part F — Expert Analysis & Root Cause:** Systematic breakdown of the exact failure mechanism.
- **Part G — Hands-on Reproduction Laboratory:** Runnable Spring Boot 3.3.5 / Java 21 code reproducing the failure.
- **Part H — Production Fix & Prevention:** Hardened solution, prevention runbooks, and Prometheus alert rules.
- **Part I — Interview & Production Questions:** 5 interview questions, 5 production incident questions, and 3 trick questions.
