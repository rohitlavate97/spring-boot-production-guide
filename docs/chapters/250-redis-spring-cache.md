---
chapter: 250
topic: Redis & Spring Cache — @Cacheable Internals, Cache Stampede Prevention, Eviction Strategies, Redis Cluster & Failover
prerequisite_chapters: [10, 20, 30, 40, 50, 60, 70, 80, 90, 100, 110, 120, 130, 140, 150, 160, 170, 180, 190, 200, 210, 220, 230, 240]
reference_system_node: Payment Service & Merchant Ledger ↔ Redis Cluster cache_redis (Spring Cache, @Cacheable, XFetch Probabilistic Expiration, Hash Slot Sharding)
---

# Chapter 250: Redis & Spring Cache — @Cacheable Internals, Cache Stampede Prevention, Eviction Strategies, Redis Cluster & Failover

## 1. Concept

In high-throughput financial platforms like FinFlow, the database cannot sustain read traffic for high-frequency operations (such as merchant fee calculation, forex rate lookup, and rate limiting) during 10,000+ req/sec traffic surges.

The latency hierarchy dictates caching architecture:

$$\underbrace{\text{L1 Local Memory (Caffeine)}}_{\approx 100\text{ ns}} \;\ll\; \underbrace{\text{L2 Distributed Cache (Redis Cluster)}}_{\approx 0.5\text{ ms}} \;\ll\; \underbrace{\text{Relational Database (PostgreSQL NVMe)}}_{\approx 10\text{ ms}}$$

Spring Boot provides a declarative caching abstraction via `@EnableCaching`, `@Cacheable`, `@CachePut`, and `@CacheEvict`. 

However, naive caching is one of the most common causes of cascading production outages. Without hardening, systems suffer from **The Big 3 Cache Failures**:
1. **Cache Stampede (Thundering Herd)**: A hot key expires, and thousands of concurrent threads miss cache simultaneously, hammering the primary database.
2. **Cache Penetration**: Malicious or non-existent keys bypass the cache completely and overwhelm the database.
3. **Cache Avalanche**: Thousands of cached keys configured with identical static TTLs expire simultaneously, dumping massive traffic spikes onto the database.

```
+-------------------------------------------------------------------------------------------------+
|                                 The Golden Rules of Production Caching                          |
|                                                                                                 |
|  1. Never Cache without Mutex Synchronization on Hot Keys: Use @Cacheable(sync = true) or       |
|     Probabilistic Early Recomputation (XFetch) to eliminate Cache Stampedes.                    |
|  2. Never Use Identical Static TTLs: Always apply TTL Jitter (+/- 10-20% randomization).        |
|  3. Never Use Default JDK Serialization: Use GenericJackson2JsonRedisSerializer with           |
|     JavaTimeModule to avoid unreadable binary payloads and ClassNotFoundException refactor traps.|
+-------------------------------------------------------------------------------------------------+
```

---

## 2. Internal Working

### Spring Cache Abstraction Architecture

When a service method is annotated with `@Cacheable`:

```
Caller ──► Spring AOP Proxy (CGLIB)
                 │
                 ▼
          CacheInterceptor (extends CacheAspectSupport)
                 │
                 ├── 1. Evaluate SpEL key (e.g. #merchantId)
                 ├── 2. Query CacheManager (RedisCache / ConcurrentMapCache)
                 │
                 ├── [Cache HIT] ──────────────────────────► Return Cached Value (Skip Method!)
                 │
                 └── [Cache MISS]
                         │
                         ├── If sync = true: Acquire Mutex Lock for key
                         │   └── Re-check cache after acquiring lock!
                         │
                         ├── Execute Target Method (Database Query)
                         │
                         ├── Store Result in Cache (TTL applied)
                         │
                         └── Return Result to Caller
```

---

### Redis Cluster Hash Slots & Slot Hash Tags

A **Redis Cluster** partitions data across 16,384 **Hash Slots**:

$$\text{Hash Slot} = \text{CRC16}(\text{key}) \pmod{16384}$$

```
┌─────────────────────────────────────────────────────────────┐
│                      Redis Cluster (6 Nodes)                │
│                                                             │
│  Master 1 (Slots 0 – 5460)      ◄── Replica 1 (Failover)    │
│  Master 2 (Slots 5461 – 10922)  ◄── Replica 2 (Failover)    │
│  Master 3 (Slots 10923 – 16383) ◄── Replica 3 (Failover)    │
└─────────────────────────────────────────────────────────────┘
```

#### Slot Hash Tags (`{...}`)
If you execute multi-key operations (transactions, MGET, Lua scripts), all keys **must reside in the exact same hash slot**, or Redis throws `CROSSSLOT Keys in request don't hash to the same slot`.

To force related keys to the same slot, wrap the tenant/merchant identifier in curly braces `{...}`:
```text
// Both keys are hashed ONLY on the string inside {MERCHANT_101}:
{MERCHANT_101}:fee_schedule   ──► CRC16("MERCHANT_101") % 16384 -> Slot 7842
{MERCHANT_101}:payout_profile ──► CRC16("MERCHANT_101") % 16384 -> Slot 7842 (Guaranteed Same Shard!)
```

---

### Serialization: JDK Serializer vs Jackson JSON

| Serializer | Wire Format | Readability | Cross-Language Support | Refactoring Resilience |
|---|---|---|---|---|
| **`JdkSerializationRedisSerializer`** *(Default)* | Java binary stream (`\xac\xed\x00\x05...`) | Unreadable | Java Only | **Fragile** *(Fails if class `serialVersionUID` or package changes!)* |
| **`GenericJackson2JsonRedisSerializer`** *(Recommended)* | JSON (`{"@class": "...", "merchantId": "..."}`) | Human-readable | JSON compatible | **Resilient** *(Handles schema evolution gracefully)* |

---

### The Big 3 Cache Disasters & Production Hardening

#### 1. Cache Stampede (Thundering Herd) & Mutex Locking
- **Problem**: Key `MERCHANT_ACME:fees` expires. Under 4,000 req/sec, 4,000 threads experience a cache miss in the same 50ms window. All 4,000 threads execute the heavy SQL query simultaneously, saturating HikariCP and taking down the database.
- **Hardened Solution 1**: Use Spring's `@Cacheable(sync = true)`. An internal mutex lock ensures only **1 thread** queries the database; the other 3,999 threads block briefly and read the cached value once populated.
- **Hardened Solution 2 (Optimal)**: **The XFetch Probabilistic Early Expiration Algorithm** (Vattani, Chierichetti, Heyer):

$$\Delta \cdot \beta \cdot (-\ln(\text{random}())) > (\text{expiryTime} - \text{currentTime})$$

Where $\Delta$ is the execution time of the database query, $\beta \ge 1.0$ is the aggressiveness multiplier, and $\text{random}() \in (0, 1]$. As expiration approaches under heavy traffic, exactly one incoming request probabilistically recomputes the cache *in advance*, ensuring the key never expires in production!

#### 2. Cache Penetration & Null Caching
- **Problem**: An attacker queries non-existent keys (e.g. `MERCHANT_NON_EXISTENT_9999`). The database returns nothing, so nothing is cached. Every subsequent query hits the database directly.
- **Hardened Solution**: Configure `setAllowNullValues(true)` on `RedisCacheConfiguration` with a short TTL (e.g. 60 seconds) or employ a **Bloom Filter** at the gateway.

#### 3. Cache Avalanche & TTL Jitter
- **Problem**: Batch loading 100,000 merchant fee schedules with a fixed 1-hour TTL (`ttl = 3600s`). Exactly 3,600 seconds later, all 100,000 keys expire at the exact same millisecond.
- **Hardened Solution**: Add **Random TTL Jitter**:

$$\text{Effective TTL} = \text{BaseTTL} \pm \text{RandomJitter}(10\%)$$

---

## 3. Enterprise Scenario: FinFlow Merchant Fee Engine

In the **FinFlow Reference Architecture**:

```
Payment Ingress (10,000 req/sec) ──► Payment Service (20 pods in Kubernetes)
                                           │
                                           ▼ (Query Fee Schedule)
                             Redis Cluster (cache_redis)
                                   ├── Hit: Returns MerchantFeeSchedule (0.4ms)
                                   └── Miss: @Cacheable(sync = true)
                                              ├── 1 thread queries PostgreSQL (payment_db)
                                              └── 9,999 threads wait and read cached result
```

- **Domain Entity**: `MerchantFeeSchedule` (`tier`, `percentageFee`, `fixedFee`, `effectiveDate`).
- **SLA**: Fee calculation must execute in $< 1\text{ ms}$ at 99.9th percentile.

---

## 4. Incorrect Implementation

Below is a vulnerable caching implementation typical of unhardened systems:

```java
package com.finflow.chapter250.incorrect;

import com.finflow.chapter250.domain.MerchantFeeSchedule;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Missing sync = true -> vulnerable to Cache Stampede.
 * 2. Un-jittered static TTL -> vulnerable to Cache Avalanche.
 * 3. Does not cache nulls -> vulnerable to Cache Penetration.
 */
@Service
public class FeeScheduleCacheServiceIncorrect {

    private final AtomicInteger dbQueryCount = new AtomicInteger(0);

    /**
     * Anti-Pattern: @Cacheable without sync=true.
     * When 50 concurrent requests hit an expired key, all 50 threads
     * miss cache and execute the heavy database query simultaneously!
     */
    @Cacheable(value = "merchant_fee_schedules", key = "#merchantId")
    public MerchantFeeSchedule getFeeScheduleUnsafe(String merchantId) {
        dbQueryCount.incrementAndGet();

        // Simulate heavy SQL query latency (50ms)
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return new MerchantFeeSchedule(
                merchantId,
                "TIER_1_ENTERPRISE",
                BigDecimal.valueOf(0.015),
                BigDecimal.valueOf(0.20),
                Instant.now(),
                1L
        );
    }
}
```

---

## 5. Production Incident

### Incident Timeline

| Time (UTC) | Event / Telemetry |
|---|---|
| **00:00:00** | Nightly batch job pre-warms the cache for 200,000 merchant fee schedules with a static TTL of 12 hours (`12 * 3600s`). |
| **12:00:00** | Black Friday peak sale begins. Ingress traffic surges to 12,500 payment requests/sec. |
| **12:00:01** | Exactly at 12:00:00, all 200,000 cached keys expire simultaneously (Cache Avalanche). |
| **12:00:02** | Over 8,000 concurrent threads across 20 pods miss the cache on hot merchant keys (Cache Stampede). |
| **12:00:05** | PostgreSQL active connection count spikes from 40 to 600 (max capacity), instantly saturating HikariCP pools across all pods. |
| **12:00:15** | Database CPU hits 100%. Query response time degrades from 2ms to 4,500ms. |
| **12:00:30** | HikariCP throws `ConnectionTimeoutException: Connection is not available, request timed out after 30000ms`. |
| **12:01:00** | API Gateway starts returning HTTP 504 Gateway Timeout on 92% of checkout attempts. SEV-0 Incident declared. **$6.4M** in transactions stalled. |
| **12:15:00** | Engineers deploy hotfix adding `@Cacheable(sync = true)` and TTL jitter ($\pm 15\%$), and restart Redis with cache pre-warming. |
| **12:22:00** | Database connections normalize to 35, P99 latency drops to 0.6ms, 100% of checkout traffic restored. |

---

## 6. Logs & Diagnostics

### 1. HikariCP Pool Starvation Caused by Cache Stampede
```text
2026-08-20T12:00:30.114Z ERROR [payment-service,trace_id=1f2e3d,span_id=4c5b6a] 1 --- [http-nio-8080-exec-114] com.zaxxer.hikari.pool.HikariPool        : HikariPool-1 - Connection is not available, request timed out after 30000ms (total=30, active=30, idle=0, waiting=1842)

org.springframework.dao.CannotAcquireLockException: could not extract ResultSet; nested exception is org.hibernate.exception.LockAcquisitionException: HikariPool-1 - Connection is not available
	at com.zaxxer.hikari.pool.HikariPool.getConnection(HikariPool.java:213)
	at com.finflow.chapter250.incorrect.FeeScheduleCacheServiceIncorrect.getFeeScheduleUnsafe(FeeScheduleCacheServiceIncorrect.java:26)
```

### 2. Redis Slowlog & Stampede Query Analysis
```bash
redis-cli -p 6379 SLOWLOG GET 5
# Output shows hundreds of duplicate GET / SET queries on the same key during the 12:00:00 window
```

---

## 7. Root Cause Analysis

```
+-------------------------------------------------------------------------------------------------+
|                                Cache Avalanche Root Cause Chain                                 |
|                                                                                                 |
|  1. Synchronized Static TTLs (Cache Avalanche)                                                  |
|     └── 200,000 keys loaded simultaneously with identical 12-hour TTL expired at 12:00:00.000.  |
|                                                                                                 |
|  2. Unsynchronized @Cacheable Misses (Cache Stampede / Thundering Herd)                         |
|     └── Missing sync = true caused 8,000 concurrent threads to run identical SQL queries.        |
|                                                                                                 |
|  3. Connection Pool Saturation & Cascading Failure                                              |
|     └── 8,000 SQL queries exhausted 30 HikariCP connections, causing thread queues to back up   |
|         until Tomcat rejected new requests with 504 Gateway Timeouts.                           |
|                                                                                                 |
|  4. Remediation: sync = true + TTL Jitter + Jackson Serialization                               |
|     └── sync = true serializes DB lookups to 1 per key; Jitter staggers expirations over 2 hrs. |
+-------------------------------------------------------------------------------------------------+
```

---

## 8. Debugging Process

```
[1. Metric Triage] Monitor Grafana for sudden drops in cache hit ratio (keyspace_hits / misses)
       │
[2. Redis Telemetry] Check INFO stats -> keyspace_misses spike
       │
[3. Connection Pool Inspection] Check HikariPool active vs waiting connections
       │
[4. Concurrency Profiling] Inspect thread dump (jstack) for threads blocked in CacheInterceptor
       │
[5. Rollout] Enable sync = true, add TTL Jitter, and verify single DB hit under load test
```

---

## 9. Correct Implementation

### 1. Domain Model: `MerchantFeeSchedule.java`

```java
package com.finflow.chapter250.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class MerchantFeeSchedule implements Serializable {

    private String merchantId;
    private String tier;
    private BigDecimal percentageFee;
    private BigDecimal fixedFee;
    private Instant effectiveDate;
    private long version;

    public MerchantFeeSchedule() {}

    public MerchantFeeSchedule(String merchantId, String tier, BigDecimal percentageFee,
                               BigDecimal fixedFee, Instant effectiveDate, long version) {
        this.merchantId = merchantId;
        this.tier = tier;
        this.percentageFee = percentageFee;
        this.fixedFee = fixedFee;
        this.effectiveDate = effectiveDate;
        this.version = version;
    }

    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getTier() { return tier; }
    public void setTier(String tier) { this.tier = tier; }
    public BigDecimal getPercentageFee() { return percentageFee; }
    public void setPercentageFee(BigDecimal percentageFee) { this.percentageFee = percentageFee; }
    public BigDecimal getFixedFee() { return fixedFee; }
    public void setFixedFee(BigDecimal fixedFee) { this.fixedFee = fixedFee; }
    public Instant getEffectiveDate() { return effectiveDate; }
    public void setEffectiveDate(Instant effectiveDate) { this.effectiveDate = effectiveDate; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
```

---

### 2. Cache Configuration with Null-Safety: `CacheConfig.java`

```java
package com.finflow.chapter250.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager();
        cacheManager.setCacheNames(List.of(
                "merchant_fee_schedules",
                "merchant_fee_schedules_hardened",
                "null_safe_merchant_fees"
        ));
        cacheManager.setAllowNullValues(true); // Protects against Cache Penetration
        return cacheManager;
    }
}
```

---

### 3. Production-Hardened Service: `FeeScheduleCacheServiceCorrect.java`

```java
package com.finflow.chapter250.correct;

import com.finflow.chapter250.domain.MerchantFeeSchedule;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class FeeScheduleCacheServiceCorrect {

    private final AtomicInteger dbQueryCount = new AtomicInteger(0);

    /**
     * Cache Stampede Protected:
     * sync = true instructs CacheManager to serialize concurrent callers on cache miss.
     * Only 1 thread queries the database; other threads wait and read the cached result.
     */
    @Cacheable(value = "merchant_fee_schedules_hardened", key = "#merchantId", sync = true)
    public MerchantFeeSchedule getFeeSchedule(String merchantId) {
        dbQueryCount.incrementAndGet();

        // Simulate database lookup latency
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        return new MerchantFeeSchedule(
                merchantId,
                "TIER_1_ENTERPRISE",
                BigDecimal.valueOf(0.015),
                BigDecimal.valueOf(0.20),
                Instant.now(),
                1L
        );
    }

    /**
     * Cache Penetration Protected: Caches null values to prevent repeated DB misses.
     */
    @Cacheable(value = "null_safe_merchant_fees", key = "#merchantId", sync = true)
    public MerchantFeeSchedule getFeeScheduleNullSafe(String merchantId) {
        dbQueryCount.incrementAndGet();

        if (merchantId.startsWith("NON_EXISTENT")) {
            return null; // Cached as NullValue
        }

        return new MerchantFeeSchedule(
                merchantId,
                "TIER_2_GROWTH",
                BigDecimal.valueOf(0.020),
                BigDecimal.valueOf(0.25),
                Instant.now(),
                1L
        );
    }

    @CachePut(value = "merchant_fee_schedules_hardened", key = "#schedule.merchantId")
    public MerchantFeeSchedule updateFeeSchedule(MerchantFeeSchedule schedule) {
        schedule.setVersion(schedule.getVersion() + 1);
        return schedule;
    }

    @CacheEvict(value = "merchant_fee_schedules_hardened", key = "#merchantId")
    public void evictFeeSchedule(String merchantId) {
        // Cache invalidated
    }

    public int getDbQueryCount() { return dbQueryCount.get(); }
    public void resetDbQueryCount() { dbQueryCount.set(0); }
}
```

---

### 4. XFetch Probabilistic Early Expiration: `XFetchProbabilisticCacheService.java`

```java
package com.finflow.chapter250.correct;

import com.finflow.chapter250.domain.MerchantFeeSchedule;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Service
public class XFetchProbabilisticCacheService {

    private static final double BETA = 1.0;
    private static final Random RANDOM = new Random();

    public record XFetchEntry<T>(
            T value,
            long deltaComputeMs,
            long expiryTimestampMs
    ) {}

    private final Map<String, XFetchEntry<MerchantFeeSchedule>> cache = new ConcurrentHashMap<>();
    private final AtomicInteger recomputationCount = new AtomicInteger(0);

    public MerchantFeeSchedule getOrCompute(String key, long ttlMs, Supplier<MerchantFeeSchedule> dbLoader) {
        long now = System.currentTimeMillis();
        XFetchEntry<MerchantFeeSchedule> entry = cache.get(key);

        if (entry != null) {
            long timeRemaining = entry.expiryTimestampMs() - now;

            // XFetch formula: delta * beta * -ln(rand) > timeRemaining
            double rand = Math.max(1e-10, RANDOM.nextDouble());
            double earlyExpirationThreshold = entry.deltaComputeMs() * BETA * (-Math.log(rand));

            if (timeRemaining > 0 && earlyExpirationThreshold < timeRemaining) {
                return entry.value(); // Safe cache hit
            }
        }

        // Cache miss or early expiration condition met: Recompute in background/in-line
        recomputationCount.incrementAndGet();
        long startCompute = System.currentTimeMillis();
        MerchantFeeSchedule computedValue = dbLoader.get();
        long deltaCompute = Math.max(1, System.currentTimeMillis() - startCompute);

        XFetchEntry<MerchantFeeSchedule> newEntry = new XFetchEntry<>(
                computedValue,
                deltaCompute,
                System.currentTimeMillis() + ttlMs
        );
        cache.put(key, newEntry);

        return computedValue;
    }

    public int getRecomputationCount() { return recomputationCount.get(); }
    public void clear() { cache.clear(); recomputationCount.set(0); }
}
```

---

## 10. Performance Comparison

Benchmarked during hot key expiration under 10,000 req/sec load.

| Metric | Unhardened Cache (@Cacheable default) | Production Hardened (@Cacheable sync=true + Jitter) |
|---|---|---|
| **Cache Stampede DB Query Spike** | 4,500 queries/sec *(Database Crash)* | **1 query/sec (Single thread DB fetch)** |
| **P99 Read Latency on Expiry** | 4,800ms *(504 Timeout)* | **0.65ms** |
| **PostgreSQL Connection Spike** | 100% (Exhausted HikariCP) | **0% (1 connection utilized)** |
| **Serialization Overhead** | High (JDK Reflection) | **Low (Jackson Streaming JSON)** |
| **Cache Penetration DB Hits** | 10,000 queries/sec | **1 query / 60s (Null Cached)** |
| **Cache Avalanche Spike** | 200,000 simultaneous misses | **Staggered smoothly over 2 hours** |

---

## 11. Best Practices

### The Do's
- **DO set `sync = true` on hot `@Cacheable` methods**: Serializes cache misses to prevent thundering herd database crashes.
- **DO add $\pm 10\%\text{--}20\%$ TTL Jitter**: Ensures distributed cache key expirations are staggered across time.
- **DO enable null-value caching (`setAllowNullValues(true)`)**: Prevents repeated database queries for non-existent entities.
- **DO use Slot Hash Tags (`{tenantId}:key`) in Redis Cluster**: Ensures related keys reside in the same hash slot for multi-key operations.
- **DO configure `GenericJackson2JsonRedisSerializer`**: Guarantees language-agnostic JSON storage and avoids Java serialization traps.

### The Don'ts
- **DON'T mutate cached objects in memory**: In local caches (Caffeine), mutating returned objects mutates the cached instance directly, causing severe concurrency bugs.
- **DON'T rely on local in-memory caches without synchronization in multi-pod deployments**: Causes data drift where Pod 1 has stale data while Pod 2 has fresh data.
- **DON'T execute long-running I/O or network calls inside `@Cacheable` methods**: Increases lock hold times when `sync = true` is active.
- **DON'T use `KEYS *` in production Redis**: Blocks the single-threaded Redis event loop; use `SCAN` instead.

---

## 12. Common Mistakes

### Mistake 1: The JDK Binary Serialization Trap
Using default Spring Boot Redis settings which store Java binary serialization strings.
**Why it fails**: When you deploy a refactored class or change packages, deserialization throws `InvalidClassException` or `ClassNotFoundException`, crashing all cache reads until Redis is flushed manually.
**Production Fix**: Configure `RedisCacheConfiguration.serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(new GenericJackson2JsonRedisSerializer()))`.

### Mistake 2: In-Memory Mutation of Cached Objects
```java
MerchantFeeSchedule schedule = feeService.getFeeSchedule("MERCHANT_101");
schedule.setFixedFee(BigDecimal.ZERO); // FATAL BUG: Mutates local cache directly!
```
**Why it fails**: Other threads querying the cache immediately see the uncommitted, in-memory mutation.
**Production Fix**: Return immutable records or clone entities before returning.

---

## 13. Interview Questions

### Junior Tier
**Q: What is the difference between `@Cacheable`, `@CachePut`, and `@CacheEvict` in Spring Boot?**
> **Answer**:
> - `@Cacheable`: Checks the cache first. If found, returns the cached value and skips method execution. If not found, executes the method and caches the result.
> - `@CachePut`: Always executes the method and updates the cache with the method's return value. Used for create/update operations.
> - `@CacheEvict`: Removes one or more entries from the cache (e.g. on delete operations), forcing subsequent calls to fetch fresh data from the primary store.

### Mid Tier
**Q: Explain the difference between Cache Stampede (Thundering Herd), Cache Penetration, and Cache Avalanche, and how to prevent each.**
> **Answer**:
> 1. **Cache Stampede**: A single hot key expires, and thousands of concurrent requests miss the cache simultaneously, hammering the database. *Prevention*: Use `@Cacheable(sync = true)` or the XFetch probabilistic early expiration algorithm.
> 2. **Cache Penetration**: Requests query non-existent keys that are never found in DB or cache, hitting the database on every call. *Prevention*: Cache null/empty values with short TTLs (`setAllowNullValues(true)`) or use a Bloom filter.
> 3. **Cache Avalanche**: A large volume of cached keys configured with identical TTLs expire at the exact same moment. *Prevention*: Apply random TTL Jitter ($\pm 10\%\text{--}20\%$).

### Senior Tier
**Q: How does `sync = true` work internally in Spring's `@Cacheable`, and what is its performance trade-off?**
> **Answer**: When `sync = true` is configured, Spring's `CacheInterceptor` coordinates access via an internal mutex lock (e.g. `ConcurrentHashMap` locks per key). When multiple threads miss the cache simultaneously for key $K$, only the first thread executes the underlying database method. The other threads block waiting on the lock. Once the first thread finishes and populates the cache, the waiting threads wake up, verify the cache is now populated, and return the cached value without touching the database. The trade-off is a slight increase in latency for the waiting threads during a miss, but it completely protects the database from connection pool exhaustion.

### Staff Tier
**Q: Explain Redis Cluster Hash Slot architecture, why `CROSSSLOT` errors occur, and how Slot Hash Tags resolve them.**
> **Answer**: Redis Cluster divides its keyspace into 16,384 discrete Hash Slots. The slot for a key is computed as `CRC16(key) % 16384`. Each master node owns a subset of slots. In multi-key commands (MGET, Lua scripts, multi-key transactions), all target keys must reside on the exact same master node in the same hash slot, otherwise Redis aborts with a `CROSSSLOT Keys in request don't hash to the same slot` error. **Slot Hash Tags** solve this: by placing a common identifier in curly braces `{...}` (e.g., `{merchant_101}:fees` and `{merchant_101}:limits`), Redis computes the CRC16 hash *only* on the text between the braces, guaranteeing that all related keys map to the same hash slot and shard.

### Principal Tier
**Q: Explain the mathematical derivation and architectural benefit of the XFetch Probabilistic Early Expiration Algorithm over static TTLs.**
> **Answer**: The XFetch algorithm (Vattani, Chierichetti, Heyer) replaces deterministic key expiration with optimal probabilistic early recomputation. When a key is read at time $t$, given remaining time $t_{\text{rem}} = \text{expiry} - t$, computation cost $\Delta$, and constant $\beta \ge 1$:
> $$\Delta \cdot \beta \cdot (-\ln(U)) > t_{\text{rem}} \quad \text{where } U \sim \text{Uniform}(0, 1)$$
> As $t_{\text{rem}} \to 0$, the probability of triggering early recomputation approaches 1. Under high concurrency ($N$ requests/sec), the expected number of recomputations is mathematically bounded to $\approx 1$, occurring *before* the key expires. The system achieves optimal read latency ($0\text{ ms}$ DB wait) with zero cache stampede spikes, completely decoupling cache invalidation from database load spikes.

---

## 14. Hands-on Exercise

### Objective
Implement a production-safe Spring Redis Cache configuration with:
1. Custom TTL per cache name (`merchant_fee_schedules` = 1 hr).
2. Random TTL Jitter of $\pm 10\%$.
3. Null value caching enabled.
4. Jackson JSON serialization with JavaTimeModule.

### Solution

```java
@Configuration
@EnableCaching
public class RedisProductionCacheConfig {

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );

        GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer(objectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30).plusSeconds(ThreadLocalRandom.current().nextInt(-180, 180)))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(serializer))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .disableCachingNullValues(); // Or enable for penetration protection

        RedisCacheConfiguration feeConfig = defaultConfig
                .entryTtl(Duration.ofHours(1).plusSeconds(ThreadLocalRandom.current().nextInt(-360, 360)))
                .setAllowNullValues(true);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withCacheConfiguration("merchant_fee_schedules", feeConfig)
                .build();
    }
}
```

---

## 15. Advanced Challenge: Two-Tier Multi-Level Cache (L1 Caffeine + L2 Redis Cluster)

### Enterprise Problem Statement
Implement an ultra-low-latency two-tier cache where L1 (local in-memory Caffeine) serves reads in $< 100\text{ ns}$, L2 (Redis Cluster) serves cache misses in $< 0.5\text{ ms}$, and cache mutations publish invalidation events over Redis Pub/Sub to synchronize all L1 caches across pods.

### Enterprise Solution

```java
@Service
public class MultiLevelCacheService {

    private final com.github.benmanes.caffeine.cache.Cache<String, MerchantFeeSchedule> l1Cache =
            Caffeine.newBuilder().maximumSize(10_000).expireAfterWrite(5, TimeUnit.MINUTES).build();

    private final RedisTemplate<String, Object> redisTemplate;

    public MultiLevelCacheService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public MerchantFeeSchedule getFeeSchedule(String merchantId, Supplier<MerchantFeeSchedule> dbLoader) {
        // Tier 1: Local In-Memory Caffeine (100ns)
        MerchantFeeSchedule cachedL1 = l1Cache.getIfPresent(merchantId);
        if (cachedL1 != null) {
            return cachedL1;
        }

        // Tier 2: Distributed Redis Cluster (0.5ms)
        MerchantFeeSchedule cachedL2 = (MerchantFeeSchedule) redisTemplate.opsForValue().get("fees:" + merchantId);
        if (cachedL2 != null) {
            l1Cache.put(merchantId, cachedL2);
            return cachedL2;
        }

        // Tier 3: Database Fallback (10ms)
        MerchantFeeSchedule fresh = dbLoader.get();
        redisTemplate.opsForValue().set("fees:" + merchantId, fresh, 1, TimeUnit.HOURS);
        l1Cache.put(merchantId, fresh);
        return fresh;
    }

    public void evict(String merchantId) {
        redisTemplate.delete("fees:" + merchantId);
        l1Cache.invalidate(merchantId);
        redisTemplate.convertAndSend("cache-invalidation-topic", merchantId); // Broadcast to all pods!
    }
}
```

---

## 16. Production Checklist

Before approving any pull request involving caching and Redis:

- [ ] **`sync = true` on Hot `@Cacheable`**: Confirm all high-concurrency `@Cacheable` methods specify `sync = true`.
- [ ] **TTL Jitter Applied**: Verify cache expirations include random jitter ($\pm 10\%\text{--}20\%$).
- [ ] **Null Values Cached**: Ensure `setAllowNullValues(true)` is enabled for penetration defense on missing entities.
- [ ] **Jackson JSON Serialization Configured**: Confirm `GenericJackson2JsonRedisSerializer` is used instead of default Java serialization.
- [ ] **Slot Hash Tags Used for Multi-Key Redis Cluster Ops**: Verify keys in multi-key operations use `{...}` hash tags.
- [ ] **No `KEYS *` in Production**: Confirm all key scans use `SCAN` or precise key lookups.
- [ ] **Cache Eviction Consistency**: Verify mutating methods (`@CachePut`, `@CacheEvict`) update or invalidate the appropriate cache key.
