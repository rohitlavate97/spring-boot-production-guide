package com.finflow.troubleshooting.module28.service;

import com.finflow.troubleshooting.module28.model.IncidentRecord;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class IncidentTriageEngine {

    public record TriageDecision(
            String incidentSeverity,
            int targetSlaMinutes,
            String incidentCommanderActionPlan,
            List<IncidentRecord> matchedArchetypes
    ) {}

    private final Map<Integer, IncidentRecord> scenarioCatalog = new LinkedHashMap<>();

    public IncidentTriageEngine() {
        populateAll20Scenarios();
    }

    private void populateAll20Scenarios() {
        scenarioCatalog.put(1, new IncidentRecord(
                1,
                "JVM Native Memory Leak & Glibc Arena Fragmentation",
                "SEV1_CRITICAL",
                "JVM_MEMORY",
                "Linux Kernel OOMKills pod; JVM Heap remains <50% used; Resident Set Size (RSS) continually grows.",
                "Glibc malloc allocates 64MB memory arenas per thread with high thread churn, causing massive fragmentation.",
                "Set MALLOC_ARENA_MAX=2 or switch container base image to jemalloc.",
                "Configure Native Memory Tracking (NMT) and limit thread pool creation using fixed executor pools.",
                "docs/module-01-jvm-memory-leaks-oom.md"
        ));

        scenarioCatalog.put(2, new IncidentRecord(
                2,
                "PostgreSQL AccessExclusiveLock Cascading Pool Exhaustion",
                "SEV1_CRITICAL",
                "DATABASE_LOCKS",
                "HikariCP connection pool exhausted in 2.5s; all incoming API transactions timeout with 500 errors.",
                "ALTER TABLE executed without lock_timeout queued behind long queries, blocking all subsequent SELECTs.",
                "Run `SELECT pg_terminate_backend(pid)` on blocking DDL query immediately to free the lock queue.",
                "Enforce `SET lock_timeout = '2s'` on all Flyway migrations and execute via Expand & Contract pattern.",
                "docs/module-25-database-migrations-flyway-locks-zero-downtime.md"
        ));

        scenarioCatalog.put(3, new IncidentRecord(
                3,
                "Kafka Consumer Lag Rebalance Death Spiral",
                "SEV1_CRITICAL",
                "KAFKA_STREAMING",
                "Consumer group continuously rebalances; consumer lag explodes; duplicate message processing.",
                "Batch processing time exceeded max.poll.interval.ms (300s), causing broker to evict consumer.",
                "Temporarily scale out consumer replicas or increase max.poll.interval.ms.",
                "Tune max.poll.records to fit within p99 batch processing time and switch to CooperativeStickyAssignor.",
                "docs/module-20-apache-kafka-consumer-lag-poison-pills-rebalances.md"
        ));

        scenarioCatalog.put(4, new IncidentRecord(
                4,
                "Redis Cache Stampede & Distributed Mutex Thundering Herd",
                "SEV1_CRITICAL",
                "REDIS_CACHE",
                "Hot cache key expires; thousands of concurrent threads query PostgreSQL simultaneously; DB CPU hits 100%.",
                "Hard TTL expiry on heavily accessed cache key with zero refresh anticipation.",
                "Pre-warm expired cache key manually or increase HikariCP pool temporarily.",
                "Implement XFetch Probabilistic Early Expiration and distributed mutex locks for cache misses.",
                "docs/module-19-redis-caching-stampede-avalanche-invalidation.md"
        ));

        scenarioCatalog.put(5, new IncidentRecord(
                5,
                "Virtual Thread Carrier Thread Pinning on Synchronized Blocks",
                "SEV2_MAJOR",
                "CONCURRENCY",
                "Virtual thread throughput collapses; carrier thread pool starvation under I/O blocking.",
                "Virtual threads calling blocking I/O inside `synchronized` blocks or native JNI methods pin carrier threads.",
                "Increase `jdk.virtualThreadScheduler.maxPoolSize` temporarily.",
                "Replace `synchronized` blocks with `java.util.concurrent.locks.ReentrantLock`.",
                "docs/module-10-virtual-threads-pinning-deadlocks.md"
        ));

        scenarioCatalog.put(6, new IncidentRecord(
                6,
                "SSL/TLS Certificate Expiry & Truststore Handshake Failures",
                "SEV1_CRITICAL",
                "NETWORKING_SECURITY",
                "Payment gateway API calls fail with `SSLHandshakeException: PKIX path building failed`.",
                "Upstream payment provider rotated root/intermediate CA certificate; JVM truststore missing new cert.",
                "Import new CA certificate into JVM cacerts keystore and perform rolling restart of pods.",
                "Deploy cert-manager with automated 30-day renewal alerts and dynamic truststore reloading.",
                "docs/module-07-ssl-tls-handshake-certificate-revocation.md"
        ));

        scenarioCatalog.put(7, new IncidentRecord(
                7,
                "Distributed Clock Skew (NTP Drift) & JWT Premature Invalidation",
                "SEV1_CRITICAL",
                "TIME_TEMPORAL",
                "100% of JWT authorization tokens rejected with `Token used before issued_at` or `Token expired`.",
                "Validating node wall-clock drifted 4 seconds ahead of token issuer node due to NTP sync failure.",
                "Restart `chrony`/`ntpd` on out-of-sync cluster nodes.",
                "Configure 10-second clock skew tolerance leeway window in JWT validator and pin JVM to UTC.",
                "docs/module-24-timezones-dst-instant-localdatetime-clock-skew.md"
        ));

        scenarioCatalog.put(8, new IncidentRecord(
                8,
                "Rolling Deployment In-Memory Session Invalidation Storm",
                "SEV1_CRITICAL",
                "DEPLOYMENT",
                "150,000 users logged out during rolling deploy; 15x login traffic surge crashes OAuth2 servers.",
                "HTTP session state stored in local JVM heap; pod termination destroyed active sessions.",
                "Throttle login gateway rate limits to prevent identity provider total failure.",
                "Migrate to stateless JWT bearer tokens or Spring Session with distributed Redis cluster.",
                "docs/module-26-deployment-failures-rolling-blue-green-canary.md"
        ));

        scenarioCatalog.put(9, new IncidentRecord(
                9,
                "Flyway Migration Lock Orphan & Deployment Rollout Stall",
                "SEV2_MAJOR",
                "DATABASE_DEVOPS",
                "New pods stuck in CrashLoopBackOff with `Unable to obtain table lock for flyway_schema_history`.",
                "Migration runner pod was killed by OOMKiller mid-migration, leaving lock row orphaned.",
                "Run `flyway repair` or manually clear the lock row in `flyway_schema_history`.",
                "Move Flyway execution out of application pods into single-replica Kubernetes Pre-Sync Jobs.",
                "docs/module-25-database-migrations-flyway-locks-zero-downtime.md"
        ));

        scenarioCatalog.put(10, new IncidentRecord(
                10,
                "Zero-Downtime Column Rename Breaking Rolling Deployments",
                "SEV1_CRITICAL",
                "DATABASE_DEVOPS",
                "50% of live production traffic fails with `column account_number does not exist`.",
                "Migration dropped/renamed column in single step while older Version 1 pods were still serving traffic.",
                "Immediately rollback frontend and recreate alias view or restore column in DB.",
                "Enforce 4-phase Expand & Contract pattern across multiple independent releases.",
                "docs/module-25-database-migrations-flyway-locks-zero-downtime.md"
        ));

        scenarioCatalog.put(11, new IncidentRecord(
                11,
                "Distributed Partial Failure & Missing Saga Compensations ($5k Money Loss)",
                "SEV1_CRITICAL",
                "DISTRIBUTED_SAGAS",
                "Customer debited $5,000; FX conversion failed; order unfulfilled and money not refunded.",
                "Synchronous REST chain without Saga Orchestration crashed mid-flow without rollback.",
                "Execute manual ledger credit adjustment for affected customer accounts.",
                "Implement Orchestrated Saga State Machine with automated reverse compensating transactions.",
                "docs/module-27-distributed-microservice-failure-sagas.md"
        ));

        scenarioCatalog.put(12, new IncidentRecord(
                12,
                "Dual-Write Loss Between PostgreSQL and Kafka Outbox",
                "SEV1_CRITICAL",
                "DISTRIBUTED_SYSTEMS",
                "Orders exist in PostgreSQL database but were never published to Kafka fulfillment topics.",
                "Application executed `save()` and `kafka.send()` non-atomically; network dropped during send.",
                "Run manual reconciliation script to scan un-published DB records and publish to Kafka.",
                "Implement Transactional Outbox Pattern to persist entity and event in single ACID transaction.",
                "docs/module-27-distributed-microservice-failure-sagas.md"
        ));

        scenarioCatalog.put(13, new IncidentRecord(
                13,
                "DNS Resolution TTL Cache Caching Stale IP After Cloud Failover",
                "SEV1_CRITICAL",
                "NETWORKING",
                "Microservices fail to connect to database endpoint after AWS RDS multi-AZ failover.",
                "JVM default `networkaddress.cache.ttl` is `-1` (cached forever), pointing to dead primary IP.",
                "Perform rolling restart of all application pods to clear JVM DNS cache.",
                "Set `networkaddress.cache.ttl=10` in `java.security` configuration.",
                "docs/module-06-dns-resolution-latency-stale-cache.md"
        ));

        scenarioCatalog.put(14, new IncidentRecord(
                14,
                "Ephemeral Container Disk Space Exhaustion via Temp Files",
                "SEV2_MAJOR",
                "STORAGE",
                "Kubernetes pod evicted with `DiskPressure` / `No space left on device` during batch uploads.",
                "Multipart file uploads left un-deleted temporary files in `/tmp` directory.",
                "Delete stale temp files manually via `kubectl exec` and restart evicted pods.",
                "Implement `try-finally` temp file deletion, streaming 8KB chunks, and mount emptyDir with sizeLimit.",
                "docs/module-23-file-uploads-storage-leaks-ephemeral-containers.md"
        ));

        scenarioCatalog.put(15, new IncidentRecord(
                15,
                "ShedLock Distributed Scheduler Overlap Under Network Partition",
                "SEV2_MAJOR",
                "SCHEDULING",
                "Billing batch job executed twice simultaneously on two pods, double-billing 10,000 customers.",
                "ShedLock `lockAtLeastFor` was omitted or set to 0, allowing second pod to acquire lock immediately.",
                "Cancel duplicate batch execution jobs and initiate customer credit memo rollback.",
                "Configure `lockAtLeastFor` to absorb clock skew and `lockAtMostFor` to protect against pod crashes.",
                "docs/module-22-scheduled-jobs-overlaps-cluster-duplication.md"
        ));

        scenarioCatalog.put(16, new IncidentRecord(
                16,
                "RabbitMQ Memory Alarm Trigger & Unacknowledged Message Flood",
                "SEV1_CRITICAL",
                "MESSAGING",
                "RabbitMQ blocks all publishers; API requests timeout with 504 Gateway Timeout.",
                "Consumers consumed messages without sending ACK/NACK; unacknowledged message queue hit memory alarm.",
                "Restart stuck consumer pods or scale consumer count to drain unacked messages.",
                "Set strict `basicQos(prefetchCount)` and configure dead-letter queues with auto-requeue=false.",
                "docs/module-16-rabbitmq-backpressure-unacked-floods.md"
        ));

        scenarioCatalog.put(17, new IncidentRecord(
                17,
                "Microservice Cascading Thread Pool Exhaustion & Bulkhead Saturation",
                "SEV1_CRITICAL",
                "RESILIENCE",
                "Slow third-party fraud API (5s latency) exhausts Tomcat threads; unrelated APIs stop responding.",
                "Lack of bulkhead isolation and timeout policies allowed slow dependency to monopolize threads.",
                "Enable circuit breaker fallback immediately via configuration toggle.",
                "Implement Resilience4j Bulkhead and CircuitBreaker with 1.5s timeout.",
                "docs/module-12-resilience4j-circuit-breakers-thread-pools.md"
        ));

        scenarioCatalog.put(18, new IncidentRecord(
                18,
                "CPU 100% Saturation via Regex Catastrophic Backtracking",
                "SEV1_CRITICAL",
                "CORE_RUNTIME",
                "All CPU cores spike to 100%; application thread dump shows threads stuck in `Pattern.matcher()`.",
                "Poorly constructed regular expression with nested quantifiers (e.g. `(a+)+$`) evaluated on crafted input.",
                "Block malicious input pattern at Web Application Firewall (WAF) level.",
                "Refactor regex using possessive quantifiers/atomic grouping and apply regex timeout safeguards.",
                "docs/module-02-cpu-spikes-thread-profiling.md"
        ));

        scenarioCatalog.put(19, new IncidentRecord(
                19,
                "Kubernetes Readiness Probe Flapping & Cascading Service Blackout",
                "SEV1_CRITICAL",
                "KUBERNETES",
                "All pods removed from Service endpoints; API gateway returns 503 Service Unavailable.",
                "Readiness probe executed heavy database queries; DB slowdown caused all probes to fail simultaneously.",
                "Increase readiness probe `timeoutSeconds` to 10s and `failureThreshold` to 5.",
                "Separate internal liveness/readiness probes (`/actuator/health/readiness`) from external dependency checks.",
                "docs/module-14-kubernetes-liveness-readiness-probes.md"
        ));

        scenarioCatalog.put(20, new IncidentRecord(
                20,
                "Out-of-Order Kafka Message Consumption Corrupting Financial Ledger",
                "SEV1_CRITICAL",
                "KAFKA_STREAMING",
                "Customer account balance negative; withdrawal processed before initial deposit due to partition hopping.",
                "Messages for same account ID published with random keys across multiple topic partitions.",
                "Freeze affected accounts and run chronological ledger replay rebuild.",
                "Enforce message key hashing on `accountId` to guarantee single-partition sequential ordering.",
                "docs/module-20-apache-kafka-consumer-lag-poison-pills-rebalances.md"
        ));
    }

    public TriageDecision triageAlert(double errorRatePercent, double p99LatencyMs, boolean isRevenueImpacting) {
        String severity;
        int slaMinutes;
        String actionPlan;

        if (isRevenueImpacting || errorRatePercent > 5.0 || p99LatencyMs > 2000.0) {
            severity = "SEV1_CRITICAL";
            slaMinutes = 15;
            actionPlan = "IMMEDIATE ESCALATION: Page Incident Commander & Tech Lead; Open War Room; Initiate traffic diversion/rollback.";
        } else if (errorRatePercent > 1.0 || p99LatencyMs > 500.0) {
            severity = "SEV2_MAJOR";
            slaMinutes = 45;
            actionPlan = "HIGH PRIORITY: Alert on-call SRE; Inspect telemetry logs and scale resources or enable circuit breaker.";
        } else {
            severity = "SEV3_MINOR";
            slaMinutes = 240;
            actionPlan = "STANDARD: Create tracking ticket; Investigate during normal business hours.";
        }

        List<IncidentRecord> matched = new ArrayList<>(scenarioCatalog.values());
        return new TriageDecision(severity, slaMinutes, actionPlan, matched);
    }

    public List<IncidentRecord> getAllScenarios() {
        return new ArrayList<>(scenarioCatalog.values());
    }

    public Optional<IncidentRecord> getScenarioById(int id) {
        return Optional.ofNullable(scenarioCatalog.get(id));
    }
}
