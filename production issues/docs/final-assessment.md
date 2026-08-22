# Final Comprehensive Assessment & Production Certification Exam

## Spring Boot Production Troubleshooting & Incident Engineering Master Assessment (50 Questions)

---

### Instructions & Exam Format
- **Total Questions:** 50
- **Time Limit:** 120 Minutes
- **Passing Score:** 80% (40 / 50 correct)
- **Domain Coverage:** All 28 modules across Core Spring, Data & Transactions, Distributed Systems, Concurrency, Networking, Kubernetes, and Incident Command.

---

### Section 1: Core Spring, Configuration & Lifecycle (Q1–Q10)

1. **In Spring Boot 3, what is the default behavior when a circular dependency is detected between two singleton beans?**
   - [ ] A) Spring automatically resolves it by generating an asynchronous proxy.
   - [x] B) Spring throws `BeanCurrentlyInCreationException` and fails application startup.
   - [ ] C) Spring converts the beans into `@RequestScope`.
   - [ ] D) Spring instantiates both beans with null fields.

2. **Which configuration source takes highest precedence in the Spring Boot externalized configuration hierarchy?**
   - [x] A) Command-line arguments (e.g. `--server.port=9090`)
   - [ ] B) Java System Properties (`-Dserver.port=9090`)
   - [ ] C) OS Environment Variables (`SERVER_PORT=9090`)
   - [ ] D) Profile-specific YAML (`application-prod.yml`)

3. **Why does calling a method annotated with `@Transactional` on `this` fail to start a database transaction?**
   - [ ] A) Java forbids reflection on `this`.
   - [x] B) Self-invocation bypasses the Spring AOP dynamic proxy wrapper.
   - [ ] C) The JVM bytecode optimizer strips the annotation at runtime.
   - [ ] D) Transactions only work on static methods.

4. **Which Maven command displays full transitive dependency trees with omitted duplicate versions?**
   - [x] A) `mvn dependency:tree -Dverbose`
   - [ ] B) `mvn clean install -X`
   - [ ] C) `mvn dependency:resolve`
   - [ ] D) `mvn compile --show-duplicates`

5. **Why should `@ControllerAdvice` never catch `java.lang.Throwable`?**
   - [ ] A) It violates HTTP 1.1 protocol rules.
   - [ ] B) It slows down JSON serialization.
   - [x] C) It swallows fatal JVM errors like `OutOfMemoryError` and `VirtualMachineError`.
   - [ ] D) Spring MVC automatically converts `Throwable` into HTTP 200.

6. **What is the effect of `@Configuration(proxyBeanMethods = false)`?**
   - [ ] A) Disables all Spring security filters.
   - [x] B) Bypasses CGLIB subclass generation; calling `@Bean` methods inside configuration executes standard Java method invocation rather than returning managed singletons.
   - [ ] C) Disables component scanning for the class.
   - [ ] D) Enforces singleton caching.

7. **How does `@Valid` on a top-level DTO behave if a nested `List<ChildDTO>` lacks `@Valid`?**
   - [ ] A) It validates all child items automatically.
   - [ ] B) It throws a runtime `ClassCastException`.
   - [x] C) It validates only the list reference itself, ignoring constraints on individual child items.
   - [ ] D) It marks the whole payload invalid.

8. **Which Jackson property prevents ISO-8601 timestamps from being automatically converted to the local system default timezone during deserialization?**
   - [ ] A) `spring.jackson.time-zone: UTC`
   - [x] B) `spring.jackson.deserialization.adjust-dates-to-context-time-zone: false`
   - [ ] C) `spring.jackson.serialization.write-dates-as-timestamps: false`
   - [ ] D) `spring.jackson.auto-detect: false`

9. **What happens if a cron expression in `@Scheduled` is scheduled for 02:30 AM in `America/New_York` during the Spring-Forward DST transition?**
   - [ ] A) The job executes at 01:30 AM.
   - [ ] B) The job runs twice.
   - [x] C) The job is completely skipped because 02:30 AM does not exist on that day.
   - [ ] D) The JVM throws `DateTimeException`.

10. **Why should `java.time.Instant` always be preferred over `java.time.LocalDateTime` for database audit fields?**
    - [x] A) `Instant` represents an absolute point in time on the UTC timeline, whereas `LocalDateTime` lacks timezone offsets and drifts when database sessions or JVMs change timezones.
    - [ ] B) `Instant` consumes less database disk space than `LocalDateTime`.
    - [ ] C) `LocalDateTime` is deprecated in Java 21.
    - [ ] D) `Instant` allows null values while `LocalDateTime` does not.

---

### Section 2: Data, JPA & Connection Pools (Q11–Q20)

11. **By default, which exception types trigger a rollback in a Spring `@Transactional` method?**
    - [ ] A) All exceptions implementing `java.lang.Exception`.
    - [x] B) Only unchecked exceptions (`RuntimeException` and `Error`).
    - [ ] C) Only `SQLException` and `DataAccessException`.
    - [ ] D) No exceptions trigger rollback automatically.

12. **What is the primary danger of `spring.jpa.open-in-view=true` in high-throughput production systems?**
    - [ ] A) It disables the Hibernate second-level cache.
    - [x] B) It holds database connections open for the entire HTTP request lifecycle, causing rapid HikariCP connection pool exhaustion under slow clients.
    - [ ] C) It causes JPA entities to be serialized to XML.
    - [ ] D) It prevents lazy loading from functioning.

13. **How does Hibernate N+1 select cascade occur?**
    - [ ] A) When $N$ transactions deadlock simultaneously.
    - [x] B) When querying $N$ parent entities triggers $N$ separate queries to fetch lazy child collections.
    - [ ] C) When the HikariCP pool has $N+1$ connections configured.
    - [ ] D) When an entity has $N+1$ column mappings.

14. **Which HikariCP configuration property specifies the maximum duration a thread will wait for a connection before throwing an exception?**
    - [ ] A) `idle-timeout`
    - [ ] B) `max-lifetime`
    - [x] C) `connection-timeout`
    - [ ] D) `validation-timeout`

15. **Why does running `ALTER TABLE accounts ADD COLUMN status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE';` without a lock timeout crash production?**
    - [ ] A) PostgreSQL does not support default values.
    - [x] B) It acquires an `AccessExclusiveLock`, queueing behind active queries and blocking all subsequent read and write traffic on the table.
    - [ ] C) It deletes all indexes on the table.
    - [ ] D) It exhausts Linux file descriptors.

16. **In PostgreSQL, how do you prevent deadlocks when concurrent transactions update multiple rows in the same table?**
    - [ ] A) Increase HikariCP pool size.
    - [x] B) Ensure all transactions sort primary keys and acquire row locks in the exact same ascending order.
    - [ ] C) Disable auto-commit on the JDBC driver.
    - [ ] D) Use `Propagation.REQUIRES_NEW`.

17. **What is the purpose of `spring.flyway.clean-disabled=true`?**
    - [ ] A) Disables Flyway validation on startup.
    - [x] B) Prevents catastrophic accidental drops of all database tables via `flyway:clean` in production.
    - [ ] C) Speeds up migration execution.
    - [ ] D) Allows out-of-order migration scripts.

18. **Why is `CREATE INDEX CONCURRENTLY` required for production PostgreSQL index creation?**
    - [ ] A) It uses less CPU than standard indexing.
    - [x] B) Standard `CREATE INDEX` acquires a `ShareLock` that blocks all concurrent write operations (`INSERT`, `UPDATE`, `DELETE`) until index creation finishes.
    - [ ] C) It automatically drops duplicate indexes.
    - [ ] D) It enables index compression.

19. **What does the "Expand" phase in the Expand and Contract database migration pattern entail?**
    - [ ] A) Dropping old columns immediately.
    - [x] B) Adding the new column as nullable and configuring application code to dual-write to both old and new columns.
    - [ ] C) Backfilling all historical data in a single blocking transaction.
    - [ ] D) Renaming the database schema.

20. **Why should `hibernate.jdbc.time_zone` be set to `UTC`?**
    - [x] A) It forces the JDBC driver to bind timestamps in UTC regardless of the host operating system's local timezone.
    - [ ] B) It enables microsecond precision on MySQL.
    - [ ] C) It speeds up date formatting in REST controllers.
    - [ ] D) It disables PostgreSQL timezone conversions.

---

### Section 3: Concurrency, Threads & Memory (Q21–Q30)

21. **What happens when a Virtual Thread executes blocking I/O inside a `synchronized` block in Java 21?**
    - [ ] A) The virtual thread is killed by the JVM.
    - [x] B) The virtual thread is "pinned" to its underlying OS carrier thread, preventing the carrier thread from executing other virtual threads.
    - [ ] C) The JVM switches to reactive non-blocking I/O.
    - [ ] D) The carrier thread pool grows infinitely.

22. **What is the root cause of Linux Kernel OOMKills (`Exit Code 137`) on containers where JVM heap is $<50\%$ utilized?**
    - [ ] A) Heap fragmentation in Young Gen.
    - [x] B) Glibc `malloc` memory arena fragmentation or off-heap native memory leaks pushing container RSS beyond cgroup limits.
    - [ ] C) Metaspace auto-resizing.
    - [ ] D) Un-indexed database queries.

23. **Which environment variable limits glibc memory arena allocation in containerized Java environments?**
    - [x] A) `MALLOC_ARENA_MAX=2`
    - [ ] B) `GLIBC_HEAP_LIMIT=512MB`
    - [ ] C) `JAVA_NATIVE_MEM=strict`
    - [ ] D) `MALLOC_TRIM_THRESHOLD=0`

24. **Why is `LinkedBlockingQueue` without a capacity bound dangerous for a Spring `@Async` `ThreadPoolTaskExecutor`?**
    - [ ] A) It rejects tasks immediately when `corePoolSize` is reached.
    - [x] B) It allows an infinite number of tasks to queue in memory, eventually causing `OutOfMemoryError: Java heap space` under heavy load.
    - [ ] C) It blocks Tomcat worker threads synchronously.
    - [ ] D) It causes threads to deadlock.

25. **Which `RejectedExecutionHandler` applies natural backpressure by forcing the calling thread to execute the rejected task?**
    - [ ] A) `AbortPolicy`
    - [ ] B) `DiscardPolicy`
    - [ ] C) `DiscardOldestPolicy`
    - [x] D) `CallerRunsPolicy`

26. **What is GC Thrashing?**
    - [ ] A) Garbage collector freeing memory too quickly.
    - [x] B) Full GC cycles running continuously trying to reclaim saturated Old Gen memory, consuming 100% CPU and pausing application threads.
    - [ ] C) Minor GC allocating objects directly into Metaspace.
    - [ ] D) JVM crashing immediately on boot.

27. **Why does taking a full heap dump on a 32GB production container via `jcmd GC.heap_dump` affect live users?**
    - [x] A) It triggers a Stop-The-World (STW) pause that freezes the JVM for 10–30 seconds.
    - [ ] B) It drops all active database tables.
    - [ ] C) It invalidates all Redis cache keys.
    - [ ] D) It revokes JWT authentication tokens.

28. **How does `-XX:+UseContainerSupport` protect Java applications in Docker/Kubernetes?**
    - [ ] A) It automatically installs Docker CLI inside the container.
    - [x] B) It configures the JVM to read cgroup memory and CPU limits instead of host physical hardware specifications.
    - [ ] C) It enables live container live-migration.
    - [ ] D) It compresses JVM thread stacks.

29. **What causes a `Metaspace` `OutOfMemoryError`?**
    - [ ] A) Too many large byte arrays in the heap.
    - [x] B) Continuous classloading and dynamic class generation (CGLIB proxies, reflection, un-cleared classloaders) exceeding `-XX:MaxMetaspaceSize`.
    - [ ] C) Database connection pool leaks.
    - [ ] D) Kafka consumer partition rebalances.

30. **What is the purpose of Native Memory Tracking (`-XX:NativeMemoryTracking=detail`)?**
    - [x] A) It tracks off-heap JVM memory allocations (Thread Stacks, Metaspace, GC data structures, C-Heap malloc).
    - [ ] B) It monitors physical host disk I/O.
    - [ ] C) It logs SQL query execution times.
    - [ ] D) It profiles CPU cache misses.

---

### Section 4: Distributed Systems, Messaging & Caching (Q31–Q40)

31. **What is a Cache Stampede (Thundering Herd) in Redis?**
    - [ ] A) Redis memory filling up and evicting all keys.
    - [x] B) A popular cache key expiring, causing thousands of concurrent requests to query the database simultaneously and spike DB CPU to 100%.
    - [ ] C) Network partition between Redis master and replica.
    - [ ] D) Corrupted serialized JSON in Redis.

32. **How does XFetch probabilistic early expiration prevent Cache Stampedes?**
    - [x] A) It probabilistically triggers a background cache refresh before the key expires based on remaining TTL and computation time.
    - [ ] B) It prevents the key from ever expiring.
    - [ ] C) It stores duplicate copies of the key across multiple Redis nodes.
    - [ ] D) It caches null values permanently.

33. **Why must Redis distributed lock release be executed via an atomic Lua script?**
    - [ ] A) Lua scripts run faster than standard Redis commands.
    - [x] B) It verifies that the lock's value matches the owner's UUID before deleting, preventing accidental release of another client's lock after lease expiry.
    - [ ] C) It encrypts the lock key.
    - [ ] D) It forces all replicas to acknowledge the deletion.

34. **What causes a Kafka consumer group to enter a continuous rebalance death spiral?**
    - [ ] A) Topic has too many partitions.
    - [x] B) Message batch processing duration exceeds `max.poll.interval.ms`, causing the broker to mark the consumer dead and trigger group rebalances.
    - [ ] C) Kafka broker runs out of disk space.
    - [ ] D) Producer publishes messages with null keys.

35. **Which Kafka partition assignment strategy minimizes partition revocations during rebalances?**
    - [ ] A) `RangeAssignor`
    - [ ] B) `RoundRobinAssignor`
    - [x] C) `CooperativeStickyAssignor`
    - [ ] D) `EagerAssignor`

36. **How does the Transactional Outbox pattern resolve the Dual-Write problem?**
    - [x] A) It saves the business entity and an outbox event in the same ACID database transaction, and uses a separate relay worker to publish to Kafka with At-Least-Once delivery.
    - [ ] B) It performs a Two-Phase Commit between PostgreSQL and Kafka.
    - [ ] C) It writes messages directly to Kafka and avoids using a database.
    - [ ] D) It uses Redis as a transaction coordinator.

37. **In an Orchestrated Saga, what is a Compensating Transaction?**
    - [ ] A) An automated refund provided by cloud cloud providers for downtime.
    - [x] B) A forward-running business operation that semantically reverses the effect of an earlier committed step after a downstream failure.
    - [ ] C) An ACID database rollback to a savepoint.
    - [ ] D) A retry mechanism that re-executes the failed step 10 times.

38. **Why must compensating endpoints in a distributed saga be idempotent?**
    - [x] A) Network timeouts or orchestrator retries can deliver the compensation message multiple times; non-idempotent endpoints would execute duplicate refunds or cancellations.
    - [ ] B) Idempotency is required by HTTP 2.0.
    - [ ] C) It reduces database storage requirements.
    - [ ] D) It prevents database row locks from being acquired.

39. **Why is `lockAtLeastFor` necessary when using ShedLock on `@Scheduled` cluster jobs?**
    - [ ] A) It prevents the database from dropping the lock table.
    - [x] B) It guarantees the lock is held for a minimum duration to prevent another pod from acquiring the lock immediately if the job finishes in milliseconds and node clocks have minor skew.
    - [ ] C) It terminates the job if it takes too long.
    - [ ] D) It disables ShedLock on local dev machines.

40. **How do you prevent a Kafka Poison Pill from causing an infinite consumer crash loop?**
    - [x] A) Configure `ErrorHandlingDeserializer` combined with `DefaultErrorHandler` and `DeadLetterPublishingRecoverer`.
    - [ ] B) Set `auto.offset.reset=latest`.
    - [ ] C) Delete the entire Kafka topic.
    - [ ] D) Disable SSL encryption on the broker.

---

### Section 5: Networking, Deployment & Incident Response (Q41–Q50)

41. **What is the Golden Rule of Incident Response during a SEV-1 production outage?**
    - [ ] A) Find the exact line of code that caused the bug before taking any action.
    - [x] B) **Mitigate First, Investigate Later!** Restore customer service immediately via rollback, pod scaling, or traffic diversion.
    - [ ] C) Reboot all database servers.
    - [ ] D) Disable all monitoring alerts.

42. **Why is cookie affinity (`canary-by-cookie`) mandatory for weighted Canary Ingress routing?**
    - [ ] A) It speeds up Nginx SSL termination.
    - [x] B) It ensures all sub-requests from a user's session land consistently on the same deployment version, preventing split-brain state mismatches.
    - [ ] C) It bypasses CORS headers.
    - [ ] D) It encrypts browser cookies.

43. **Why does AWS RDS multi-AZ failover cause microservices to fail with `UnknownHostException` or connection timeouts if JVM DNS TTL is unconfigured?**
    - [ ] A) AWS RDS drops all database users during failover.
    - [x] B) The JVM default DNS cache TTL is `-1` (cached forever), causing the application to query the stale IP address of the dead primary instance.
    - [ ] C) PostgreSQL changes port numbers during failover.
    - [ ] D) Linux kernel deletes the `/etc/hosts` file.

44. **What is the difference between a Kubernetes Liveness Probe and a Readiness Probe?**
    - [x] A) Liveness restarts deadlocked pods; Readiness removes unhealthy pods from Service endpoints without restarting them.
    - [ ] B) Liveness checks CPU usage; Readiness checks memory usage.
    - [ ] C) Readiness runs only once on startup; Liveness runs continuously.
    - [ ] D) They are identical aliases.

45. **Why should external database or third-party API health checks be EXCLUDED from Kubernetes Liveness Probes?**
    - [ ] A) Kubernetes does not allow network calls in probes.
    - [x] B) If the database slows down, liveness probes on all pods fail simultaneously, triggering a cascading restart storm across the entire cluster.
    - [ ] C) It consumes too many database licenses.
    - [ ] D) It disables Actuator metrics.

46. **What causes a `504 Gateway Timeout` from an Nginx reverse proxy?**
    - [ ] A) Nginx crashed due to out-of-memory.
    - [x] B) The upstream Spring Boot application accepted the connection but failed to complete processing and send a response before `proxy_read_timeout` expired.
    - [ ] C) The client sent an invalid TLS certificate.
    - [ ] D) The DNS record was missing.

47. **What is the difference between MTTA, MTTD, and MTTR?**
    - [x] A) MTTD = Time to detect incident onset; MTTA = Time from alert to engineer response; MTTR = Time taken to mitigate and restore service.
    - [ ] B) MTTD = Mean Time To Deploy; MTTA = Mean Time To Audit; MTTR = Mean Time To Rollback.
    - [ ] C) MTTR = Mean Time To Restart.
    - [ ] D) They are network latency metrics.

48. **In the Incident Command System (ICS), who has the authority to make the final decision to rollback a production release?**
    - [ ] A) The Software Engineer who wrote the code.
    - [x] B) The Incident Commander (IC).
    - [ ] C) The Communications Lead.
    - [ ] D) The Product Owner.

49. **What is the primary objective of a Blameless Post-Mortem?**
    - [ ] A) Assign disciplinary action to the engineer who triggered the failure.
    - [x] B) Identify systemic vulnerabilities, process gaps, and missing automated safeguards to prevent future occurrences without attributing fault to individuals.
    - [ ] C) Calculate the financial bonus deductions for the SRE team.
    - [ ] D) Archive incident logs for legal compliance.

50. **What is Clock Skew Leeway in JWT token validation?**
    - [x] A) A configurable tolerance window (e.g. 5–10s) subtracted from validation checks to absorb physical server NTP clock drift and prevent false token rejections.
    - [ ] B) The time allowed to generate a new cryptographic key.
    - [ ] C) The delay before logging out an idle user.
    - [ ] D) A rate-limiting algorithm for OAuth2 endpoints.

---

### 📊 Scoring Rubric & Certification Levels

- **45–50 Correct (90–100%):** 🌟 **Master Production SRE & Principal Architect** (Exceptional production command)
- **40–44 Correct (80–89%):** ✅ **Senior Production Debugging Engineer** (Certified for high-stakes on-call)
- **35–39 Correct (70–79%):** ⚠️ **Intermediate Troubleshooting Practitioner** (Needs review of distributed systems and database locks)
- **<35 Correct (<70%):** ❌ **Uncertified** (Re-study Modules 01–28)

---

*(End of Final Comprehensive Assessment)*
