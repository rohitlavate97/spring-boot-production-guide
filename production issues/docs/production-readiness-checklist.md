# Master Production Readiness Checklist

## Comprehensive 50-Point Enterprise Spring Boot Pre-Production Audit

---

### Domain 1: JVM Runtime, Memory & Container Ergonomics
- [ ] 1. **Java Version:** Target runtime is Java 21 LTS or newer.
- [ ] 2. **Cgroup Awareness:** JVM configured with `-XX:+UseContainerSupport` and `-XX:MaxRAMPercentage=75.0`.
- [ ] 3. **Native Memory Allocator:** Container uses `jemalloc` or sets `MALLOC_ARENA_MAX=2` to prevent glibc arena fragmentation.
- [ ] 4. **Heap Dump on OOM:** `-XX:+HeapDumpOnOutOfMemoryError` and `-XX:HeapDumpPath=/var/log/dumps` configured.
- [ ] 5. **Timezone Pinning:** JVM default timezone strictly pinned to UTC via `TimeZone.setDefault(TimeZone.getTimeZone("UTC"))`.
- [ ] 6. **DNS Cache TTL:** `networkaddress.cache.ttl=10` set in `java.security` to prevent caching stale IPs after AWS RDS failovers.
- [ ] 7. **Virtual Thread Pinning:** No `synchronized` blocks wrapping blocking I/O calls; verified via `-Djdk.tracePinnedThreads=full`.

---

### Domain 2: Database & Connection Pools (HikariCP & JPA)
- [ ] 8. **Open Session in View:** `spring.jpa.open-in-view=false` explicitly configured.
- [ ] 9. **Hibernate DDL Mode:** `spring.jpa.hibernate.ddl-auto=validate` (NEVER `update` or `create-drop` in production).
- [ ] 10. **Batch Fetching:** `hibernate.default_batch_fetch_size=100` enabled to eliminate N+1 select cascades.
- [ ] 11. **HikariCP Pool Sizing:** Pool sized accurately ($\text{Core Count} \times 2 + \text{Spindles}$); `maximum-pool-size` set between 20–50.
- [ ] 12. **HikariCP Timeouts:** `connection-timeout=30000`, `validation-timeout=3000`, and `leak-detection-threshold=15000` configured.
- [ ] 13. **Deterministic Lock Ordering:** Multi-row transactional updates sort primary keys in ascending order to prevent deadlocks.
- [ ] 14. **Temporal Types:** All point-in-time timestamp columns mapped to `java.time.Instant` and `TIMESTAMP WITH TIME ZONE`.

---

### Domain 3: Zero-Downtime Database Migrations (Flyway)
- [ ] 15. **Flyway Clean Disabled:** `spring.flyway.clean-disabled=true` strictly enforced.
- [ ] 16. **Lock Timeout in DDL:** All SQL migrations include `SET lock_timeout = '2s';`.
- [ ] 17. **Expand and Contract:** Destructive schema changes (renames/drops) divided into 4-phase rollout across independent releases.
- [ ] 18. **Concurrent Indexing:** PostgreSQL indexes created using `CREATE INDEX CONCURRENTLY` outside transaction blocks.
- [ ] 19. **Single-Replica Migration Runner:** Flyway executed via Kubernetes Pre-Sync Job / Helm Hook rather than inside application pods.

---

### Domain 4: Distributed Caching & Redis
- [ ] 20. **TTL Jitter:** All cached keys include random expiration jitter ($\pm 10\%$) to prevent Cache Avalanche.
- [ ] 21. **Cache Stampede Guard:** High-traffic hot keys protected with XFetch probabilistic refresh or distributed mutex locks.
- [ ] 22. **Cache Penetration Protection:** Non-existent database lookups cached as sentinel null markers with short TTL (e.g. 60s).
- [ ] 23. **Atomic Lua Lock Release:** Redis distributed locks released ONLY via Lua script verifying owner UUID before `DEL`.

---

### Domain 5: Messaging & Apache Kafka
- [ ] 24. **Poison Pill Resilience:** Consumers configure `ErrorHandlingDeserializer` with `DeadLetterPublishingRecoverer` to `.DLT` topics.
- [ ] 25. **Rebalance Optimization:** `partition.assignment.strategy` configured with `CooperativeStickyAssignor`.
- [ ] 26. **Poll Interval Budgeting:** `max.poll.records` budgeted to ensure batch execution completes within $70\%$ of `max.poll.interval.ms`.
- [ ] 27. **Producer Idempotence:** `enable.idempotence=true` and `acks=all` configured on all financial event producers.
- [ ] 28. **Transactional Outbox:** Multi-service event publishing implements the Transactional Outbox Pattern to eliminate dual-write loss.

---

### Domain 6: HTTP, Timeouts & Distributed Resilience
- [ ] 29. **Explicit HTTP Timeouts:** All `RestClient` / `WebClient` instances define explicit Connect Timeout (1–2s) and Read Timeout (3–5s).
- [ ] 30. **Circuit Breakers & Bulkheads:** External API calls isolated via Resilience4j `@CircuitBreaker` and `@Bulkhead`.
- [ ] 31. **Graceful Shutdown:** `server.shutdown=graceful` and `spring.lifecycle.timeout-per-shutdown-phase=30s` configured.
- [ ] 32. **Streaming File Uploads:** Multipart uploads processed via streaming `InputStream` with bounded buffers (8KB chunks); temporary files deleted in deterministic `try-finally` blocks.
- [ ] 33. **Clock Skew Leeway:** Distributed token validation applies a $\pm 10\text{s}$ clock skew tolerance window.

---

### Domain 7: Kubernetes, Ingress & Incident Readiness
- [ ] 34. **Probes Separation:** Internal liveness probes separated from external readiness probes.
- [ ] 35. **Sticky Canary Affinity:** Canary Ingress manifests define `canary-by-cookie: canary_affinity` to prevent split-brain routing.
- [ ] 36. **Stateless Pods:** User session state stored in Redis or stateless JWTs (zero in-memory sessions).
- [ ] 37. **Ephemeral Disk Limits:** `/tmp` directory mounted as `emptyDir` with `sizeLimit`.
- [ ] 38. **Structured Logging:** Async JSON logging enabled with MDC `traceId` and `spanId` propagation.
- [ ] 39. **ShedLock Scheduling:** Cluster cron jobs locked via `@SchedulerLock` with `lockAtLeastFor` and `lockAtMostFor`.
- [ ] 40. **Incident Playbooks & Runbooks:** On-call team trained on the Universal 14-Step Production Incident Debugging Protocol.

---

*(End of Master Production Readiness Checklist)*
