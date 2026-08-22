# Master Symptom-Cause-Evidence Matrix

## Comprehensive Cross-Module Troubleshooting & Incident Triage Matrix

---

| # | Observable Symptom | Primary Root Cause | Diagnostic Evidence / Command | Immediate Mitigation | Permanent Remediation | Module Ref |
|---|---|---|---|---|---|---|
| **01** | `Circular depends-on relationship between beans` | Direct circular injection between `@Service` singletons | Startup log / `BeanCurrentlyInCreationException` | Add `@Lazy` to one injection point | Refactor shared logic into a mediator service or decouple via events | **Module 01** |
| **02** | Config values ignored in production container | Environment variable relaxed binding mismatch / Profile precedence | `curl /actuator/env` | Override via CLI arg `--key=val` | Enforce `@ConfigurationProperties` with strong typing | **Module 02** |
| **03** | `NoSuchMethodError` / `ClassNotFoundException` at runtime | Transitive library JAR version collision on classpath | `mvn dependency:tree -Dverbose` | Exclude older artifact in `pom.xml` | Enforce `<dependencyManagement>` and Maven Enforcer Plugin | **Module 03** |
| **04** | HTTP 400 without hitting controller method | Jackson JSON deserialization or `@Valid` constraint failure | Inspect `MethodArgumentNotValidException` | Fix client JSON payload structure | Add custom `ProblemDetail` (RFC 7807) exception handler | **Module 04/05** |
| **05** | `@Transactional` / `@Async` fails silently | Self-invocation bypasses Spring AOP dynamic proxy | Method executes synchronously on caller thread | Call via self-injected proxy bean | Refactor method into separate `@Service` component | **Module 06** |
| **06** | Checked exception does NOT rollback database | `@Transactional` rolls back only for unchecked exceptions | DB row committed despite thrown checked exception | Declare `@Transactional(rollbackFor=Exception.class)` | Use unchecked domain exceptions | **Module 07** |
| **07** | High DB latency; hundreds of small SELECT queries | Hibernate N+1 query problem on lazy relationships | `show-sql: true` or OpenTelemetry trace spans | Add `JOIN FETCH` / `@EntityGraph` | Set `hibernate.default_batch_fetch_size: 100` | **Module 08** |
| **08** | `Connection is not available, request timed out after 30000ms` | HikariCP pool exhaustion caused by slow queries or OSIV | `pg_stat_activity` / HikariCP JMX active connections | Increase pool size temporarily | Disable OSIV (`open-in-view: false`) and optimize slow queries | **Module 09** |
| **09** | `deadlock detected` in PostgreSQL | Concurrent multi-row updates acquiring locks in opposing order | PostgreSQL error log showing conflicting transaction PIDs | Terminate blocking backend PID | Sort lock keys deterministically (`ORDER BY id`) | **Module 10** |
| **10** | CORS preflight `OPTIONS` fails with 401/403 | Spring Security filter chain placed ahead of `CorsFilter` | Browser console `Access-Control-Allow-Origin missing` | Add `.cors(Customizer.withDefaults())` | Configure explicit `CorsConfigurationSource` bean | **Module 11** |
| **11** | Slow third-party API exhausts Tomcat worker threads | Unbounded HTTP client timeouts cascading into thread pool | Thread dump shows `http-nio` threads in `TIMED_WAITING` | Enable circuit breaker fallback | Configure Resilience4j Bulkhead + Timeouts (1.5s) | **Module 12** |
| **12** | Unbounded async queue crashes JVM with `Java heap space` | `LinkedBlockingQueue` buffer overflow under load | Heap dump shows millions of `FutureTask` objects | Switch to `CallerRunsPolicy` rejection handler | Use bounded `ArrayBlockingQueue` with backpressure | **Module 13** |
| **13** | Pod OOMKilled but JVM heap $<50\%$ utilized | Glibc `malloc` arena fragmentation or C-heap native leak | `jcmd <PID> VM.native_memory detail.diff` | `MALLOC_ARENA_MAX=2` | Switch to `jemalloc` container base image | **Module 01/14** |
| **14** | MDC trace IDs lost across `@Async` thread boundaries | `ThreadLocal` storage not propagated to worker threads | Application logs show `[traceId= ]` blank | Wrap executor with `TaskDecorator` | Configure Micrometer Observation context propagation | **Module 15** |
| **15** | Docker container killed by host OOMKiller (`Exit Code 137`) | JVM unaware of cgroup memory limit | `dmesg -T \| grep -i oom-killer` | Increase cgroup memory limit | Enable `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75` | **Module 16** |
| **16** | All pods fail Readiness Probes simultaneously | Readiness probe executing slow heavy DB query | `kubectl describe service` shows 0 endpoints | Relax probe timeout / threshold | Separate internal readiness probe from external DB checks | **Module 17** |
| **17** | `504 Gateway Timeout` from Nginx | Upstream Spring Boot processing exceeds `proxy_read_timeout` | Nginx `error.log` upstream timed out | Increase `proxy_read_timeout` | Optimize upstream database queries & API latency | **Module 18** |
| **18** | Redis key expiry triggers 100% DB CPU spike | Cache Stampede on expired hot key | Database active connection surge | Pre-warm key via `redis-cli SET` | Implement XFetch probabilistic refresh + mutex locks | **Module 19** |
| **19** | Kafka consumer group continuously rebalances | Batch processing duration exceeds `max.poll.interval.ms` | `kafka-consumer-groups.sh --describe` | Scale consumer replicas | Reduce `max.poll.records` and use `CooperativeStickyAssignor` | **Module 20** |
| **20** | Distributed lock released prematurely deleting foreign lock | Non-atomic `GET` + `DEL` in Redis lock release | Audit log shows two pods executing critical section | Increase lock lease time | Use atomic Lua script verifying owner UUID before `DEL` | **Module 21** |
| **21** | Batch cron executes twice in multi-pod cluster | `@Scheduled` runs on every JVM instance | Audit records show duplicate billing | Kill duplicate cron execution | Implement ShedLock with `lockAtLeastFor` and `lockAtMostFor` | **Module 22** |
| **22** | Container evicted with `DiskPressure` during file uploads | Orphaned temporary files in `/tmp` directory | `df -h /tmp` | Delete stale temp files via `find /tmp -delete` | Use streaming `InputStream` and delete temp files in `finally` | **Module 23** |
| **23** | Audit timestamps shift by 4–5 hours across regions | Entity uses `LocalDateTime` instead of `Instant` | PostgreSQL column shows timezone offset drift | Force JVM timezone to UTC | Replace `LocalDateTime` with `Instant` (UTC) | **Module 24** |
| **24** | `ALTER TABLE` locks table, exhausting connection pool | DDL requests `AccessExclusiveLock` without timeout | `pg_locks` shows blocking DDL PID | `SELECT pg_terminate_backend(pid)` | Add `SET lock_timeout = '2s'` and follow Expand & Contract | **Module 25** |
| **25** | Rolling deploy logs out 150,000 active users | In-memory Tomcat session state destroyed on pod restart | Sudden 15x login traffic surge | Throttle login gateway rate limits | Migrate to stateless JWT or Spring Session (Redis) | **Module 26** |
| **26** | Partial multi-service failure results in un-refunded funds | Synchronous REST calls without Saga Orchestration | Database contains orphaned pending order with debited wallet | Manual ledger credit adjustment | Implement Orchestrated Saga with automated reverse compensation | **Module 27** |
| **27** | DB commits order but Kafka fulfillment event is lost | Dual-write problem (DB commit succeeded, Kafka send failed) | Unfulfilled orders in DB missing from Kafka topic | Run batch reconciliation script | Implement Transactional Outbox Pattern | **Module 27** |
| **28** | SEV-1 cascading production outage | Multi-fault interaction across deploy, locks, and streaming | PagerDuty alert / Error rate $>5\%$ | Follow 14-step ICS Runbook | Conduct Blameless Post-Mortem & deploy automated guards | **Module 28** |

---

*(End of Master Symptom-Cause-Evidence Matrix)*
