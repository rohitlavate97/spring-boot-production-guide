# Module 19: Redis Caching: Stampede, Avalanche & Invalidation

## Issue 19.1: Cache Stampede (Thundering Herd), Cache Avalanche (Synchronized Expiration), and Dual-Write Race Inconsistencies

---

### 1. Scenario

During international market opening on the **FinFlow Global Treasury & FX Settlement Engine**:
1. The global foreign exchange rate key (`fx:USD_EUR`) cached in Redis expired during a **15,000 req/sec traffic surge**.
2. Because the application used standard unprotected Cache-Aside (`if cache == null { queryDb(); setCache(); }`), all 15,000 concurrent threads simultaneously missed the cache and slammed the primary PostgreSQL database with **15,000 identical `SELECT rate FROM exchange_rates WHERE pair = 'USD_EUR'` queries**.
3. The database connection pool instantly saturated: HikariCP threw hundreds of `Connection is not available, request timed out after 30000ms`, database CPU spiked to **100%**, and all other transaction clearing services sharing the database ground to a complete halt (**The Classic Cache Stampede / Thundering Herd**).
4. Earlier that night at 00:00 UTC, a daily catalog warmup job cached 250,000 merchant fee profiles with a fixed TTL of `3600 seconds` (1 hour). Exactly at 01:00 UTC, all 250,000 keys expired simultaneously (**The Cache Avalanche**), dropping cluster cache hit ratio from **99.4% to 2.1%** and causing a secondary database outage.
5. In addition, an automated security scanner fired 100,000 requests for non-existent account numbers (`/api/v1/accounts/ACC-INVALID-*`). Because the application did not cache null values, every single request penetrated directly to the database (**Cache Penetration**).
6. To make matters worse, an exchange rate update service executed `db.update()` followed by `redis.del(key)`. A concurrent reader running on another node read the old DB state before the write committed and re-cached the stale value in Redis, causing **$1.8M in settlement transactions to execute on stale exchange rates for 4 hours**!

---

### 2. Symptoms

```text
1. Database Connection Pool Starvation on Hot Key Expiry (Stampede):
   HikariPool-1 - Connection is not available, request timed out after 30000ms.
   PostgreSQL pg_stat_activity shows hundreds of identical queries in "active" state.

2. Periodic Catastrophic Cache Hit Ratio Drops (Avalanche):
   Prometheus redis_hit_ratio drops off a cliff every hour on the hour (e.g. 99% -> 2%).
   Database CPU and read IOPS experience massive periodic saw-tooth spikes.

3. Cache Bypass on Non-Existent Entities (Penetration):
   High database read load on primary keys that do not exist, despite Redis running at low memory.

4. Distributed Stale Data Corruption (Dual-Write Race):
   Redis contains outdated entity state even though the database has the latest version.

5. Redis Single-Threaded Latency Spikes (Big Keys):
   Redis latency spikes from <1ms to 250ms during DEL operations on massive 50MB Hash/List keys.
```

---

### 3. Possible Root Causes

1. **Unsynchronized Cache-Aside on Hot Keys:** Missing mutex locking or probabilistic early refresh when hot keys expire, allowing unbounded concurrent threads to hammer the database.
2. **Deterministic TTL Allocation:** Using a fixed TTL (e.g. exactly 1 hour) across large batches of cached records instead of applying randomized TTL jitter.
3. **Missing Null Object Caching:** Failing to cache negative lookups (`null` results) with a short TTL, allowing repeated invalid queries to bypass the cache.
4. **Dual-Write Concurrency Races:** Updating the database and deleting the cache without Delayed Double Deletion or transactional write-through patterns.
5. **Java Native Serialization Overhead & Big Keys:** Storing large binary objects via `JdkSerializationRedisSerializer` that block Redis I/O and consume 5x more memory than structured JSON.

---

### 4. Architecture Context: Cache Stampede Guard & TTL Jitter Mechanics

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                      DISTRIBUTED CACHE RESILIENCE & STAMPEDE GUARD                              │
│                                                                                                 │
│  [15,000 Concurrent Requests for Expired Key: "fx:USD_EUR"]                                     │
│                                │                                                                │
│                                ▼                                                                │
│  ┌───────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ CacheStampedeGuardService                                                                 │  │
│  │ 1. Check Redis Cache -> MISS (Key Expired)                                                │  │
│  │ 2. Attempt Distributed Mutex Lock: SET lock:fx:USD_EUR <UUID> NX PX 5000                  │  │
│  └─────────────────────────────┬─────────────────────────────────────────────┬───────────────┘  │
│                                │ (Lock Acquired - 1 Thread)                  │ (Lock Busy - 14,999 Threads)
│                                ▼                                             ▼                  │
│  ┌──────────────────────────────────────────────────────────┐ ┌──────────────────────────────┐  │
│  │ WINNER THREAD (1 Thread Only):                           │ │ WAITING THREADS:             │  │
│  │ 1. Query PostgreSQL: SELECT rate FROM fx WHERE ...      │ │ 1. Sleep 50ms (Backoff)      │  │
│  │ 2. Compute Jittered TTL: BaseTTL (300s) + Jitter (42s)   │ │ 2. Re-check Redis Cache      │  │
│  │ 3. SET fx:USD_EUR 0.9215 EX 342                          │ │ 3. Cache HIT! Return value   │  │
│  │ 4. Release Lock: DEL lock:fx:USD_EUR                     │ │    (0 Database Queries!)     │  │
│  └─────────────────────────────┬────────────────────────────┘ └──────────────────────────────┘  │
│                                │                                                                │
│                                ▼                                                                │
│  ┌───────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ PostgreSQL Primary Database (Receives EXACTLY 1 Query instead of 15,000!)                 │  │
│  └───────────────────────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### ❌ Anti-Pattern 1: Unprotected Cache-Aside (Guaranteed Stampede)
```java
// ❌ ANTI-PATTERN: When key expires under high concurrency, all threads hit DB simultaneously!
public Double getExchangeRate(String pair) {
    Double rate = (Double) redisTemplate.opsForValue().get("fx:" + pair);
    if (rate == null) {
        rate = databaseRepository.findRate(pair); // 15,000 threads hit DB at once!
        redisTemplate.opsForValue().set("fx:" + pair, rate, Duration.ofSeconds(300));
    }
    return rate;
}
```

#### ❌ Anti-Pattern 2: Fixed TTL Batch Caching (Cache Avalanche)
```java
// ❌ ANTI-PATTERN: All 250,000 keys expire at the EXACT same second!
public void warmupCatalog(List<MerchantProfile> profiles) {
    for (MerchantProfile profile : profiles) {
        redisTemplate.opsForValue().set("merchant:" + profile.getId(), profile, Duration.ofSeconds(3600));
    }
}
```

#### ❌ Anti-Pattern 3: Ignoring Null Lookups (Cache Penetration)
```java
// ❌ ANTI-PATTERN: If account doesn't exist, nothing is cached; every query hits DB!
public Account getAccount(String id) {
    Account acc = (Account) redisTemplate.opsForValue().get("account:" + id);
    if (acc == null) {
        acc = databaseRepository.findById(id); // Returns null for invalid ID
        if (acc != null) {
            redisTemplate.opsForValue().set("account:" + id, acc, Duration.ofMinutes(10));
        }
    }
    return acc;
}
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Inspect Redis Slowlog & Big Keys
```bash
# Check Redis Slow Queries (>10ms execution)
redis-cli slowlog get 10

# Scan for memory-hogging Big Keys
redis-cli --bigkeys
```

#### Method 2: Check PostgreSQL Active Queries During Stampede
```sql
SELECT pid, age(clock_timestamp(), query_start), query, state
FROM pg_stat_activity
WHERE state != 'idle' AND query LIKE '%exchange_rates%'
ORDER BY query_start ASC;
```

#### Method 3: Monitor Real-Time Redis Commands (⚠️ Do not run blindly in production: MONITOR degrades Redis throughput by up to 50%)
```bash
redis-cli monitor | grep -E "fx:USD_EUR"
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Identify Missing Keys & Stampede Victims.
        Check if database query spikes correlate with the expiration of specific hot keys.

Step 2: Implement Distributed Mutex Lock (SETNX) for Hot Keys.
        Wrap cache misses in a mutex lock so only 1 thread recomputes the value while others wait.

Step 3: Add TTL Jitter to Batch Cached Keys.
        Add randomized jitter (e.g. +/- 10% to 20%) to all TTLs to distribute expirations uniformly.

Step 4: Enable Null Value Caching.
        Cache sentinel `NULL_SENTINEL` values with a 60-second TTL for non-existent entities.

Step 5: Apply Delayed Double Deletion on DB Updates.
        Update DB -> Delete Redis Key -> Sleep 500ms -> Delete Redis Key again.
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. The XFetch Probabilistic Early Expiration Algorithm
Instead of waiting for a key to strictly expire at $t = \text{TTL}$, the **XFetch algorithm** determines probabilistically whether a reading thread should proactively refresh the cache in the background:
$$\Delta - \beta \cdot \delta \cdot \ln(\text{rand}(0, 1)) > \text{remaining\_TTL}$$
Where:
- $\Delta$: Expiration time
- $\delta$: Time taken to compute the value from the database
- $\beta$: Greediness factor ($\beta > 0$, default 1.0)
- $\text{rand}(0, 1)$: Uniform random float between 0 and 1

As `remaining_TTL` shrinks, the probability of early recomputation approaches 1.0, ensuring hot keys are seamlessly refreshed **before they ever expire**, completely eliminating cache misses!

#### 2. The Mechanics of Cache Avalanche (Synchronized Expiration)
When $N$ keys are written with identical TTL $T$, all $N$ keys expire at $t = T$. If $N = 250,000$ and request rate is $R = 5,000\text{ req/s}$, the database experiences an instantaneous jump from $0.6\%\text{ misses}$ to $100\%\text{ misses}$.
By adding random jitter $J \sim \text{Uniform}(0, J_{\max})$:
$$\text{TTL}_i = T + \text{rand}(0, J_{\max})$$
The expirations are uniformly distributed over $[T, T + J_{\max}]$, smoothing the database load to:
$$\text{Load} = \frac{N}{J_{\max}} \text{ keys/second}$$

#### 3. The Dual-Write Race Condition
1. Thread A updates DB with Value 2.
2. Thread B reads DB (sees Value 1 if before commit).
3. Thread A deletes Redis key.
4. Thread B writes stale Value 1 into Redis.
5. **Result:** Redis permanently serves stale Value 1 until next TTL expiry.
6. **Solution (Delayed Double Deletion):** Thread A deletes the key, commits, sleeps 500ms, and deletes the key *again*, wiping out any stale write written by Thread B.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Cache Stampede Guard Service (`CacheStampedeGuardService.java`)
```java
@Service
public class CacheStampedeGuardService {

    public <T> T getOrComputeWithMutex(String key, Supplier<T> dbLoader, long customTtlSec) {
        // 1. Check cache
        CacheEntry entry = inMemoryCache.get(key);
        if (entry != null && !entry.isExpired()) {
            return (T) entry.value();
        }

        // 2. Acquire Mutex Lock (SET lock:key uuid NX PX 5000)
        String lockKey = "lock:" + key;
        String lockOwnerId = UUID.randomUUID().toString();

        for (int attempt = 0; attempt < 20; attempt++) {
            if (tryAcquireLock(lockKey, lockOwnerId)) {
                try {
                    // Double-check cache
                    CacheEntry doubleCheck = inMemoryCache.get(key);
                    if (doubleCheck != null && !doubleCheck.isExpired()) {
                        return (T) doubleCheck.value();
                    }

                    T value = dbLoader.get();
                    long ttl = (customTtlSec > 0) ? customTtlSec : computeJitteredTtlSec();
                    inMemoryCache.put(key, new CacheEntry(value != null ? value : NULL_SENTINEL, 
                            Instant.now().plusSeconds(value != null ? ttl : 60), 0));
                    return value;
                } finally {
                    releaseLock(lockKey, lockOwnerId);
                }
            } else {
                // Wait with backoff and retry
                Thread.sleep(50);
                CacheEntry retryEntry = inMemoryCache.get(key);
                if (retryEntry != null && !retryEntry.isExpired()) {
                    return (T) retryEntry.value();
                }
            }
        }
        return dbLoader.get(); // Degraded fallback
    }

    public long computeJitteredTtlSec() {
        return 300 + ThreadLocalRandom.current().nextLong(0, 61); // 300s + [0..60s] jitter
    }
}
```

#### ✅ Fix 2: Production Redis JSON Serializer Configuration
```java
@Configuration
public class RedisResilienceConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }
}
```

---

### 10. Verification

1. **Stampede Concurrency Test:** Run `CacheStampedeGuardTest.java` to verify that 20 concurrent threads for an expired key trigger exactly 1 database query.
2. **TTL Jitter Distribution Test:** Run `CacheJitterCalculatorTest.java` to verify that randomized TTLs fall within configured bounds.
3. **Null Sentinel Penetration Test:** Run `CachePenetrationGuardTest.java` to verify that non-existent entity queries do not hammer the database.
4. **Integration Test:** Run `Module19IntegrationTest.java` to verify Spring context and Actuator metrics.

---

### 11. Prevention & Production Readiness

1. **Rule: Always Use Distributed Locks on Critical Hot Keys:**
   Never allow unbounded threads to query the database on cache miss for high-traffic keys.
2. **Rule: Never Cache Large Batches with Constant TTL:**
   Always inject randomized jitter ($\pm 10\%$ to $\pm 20\%$) into cache expiration times.
3. **Prometheus Alerting Rule for Cache Hit Ratio Degradation:**
```yaml
- alert: RedisCacheHitRatioLow
  expr: sum(rate(redis_keyspace_hits_total[5m]))
        / (sum(rate(redis_keyspace_hits_total[5m])) + sum(rate(redis_keyspace_misses_total[5m]))) * 100 < 80
  for: 3m
  labels:
    severity: warning
  annotations:
    summary: "Redis Cache Hit Ratio dropped below 80% (Current: {{ $value }}%)"
```

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: What is the difference between Cache Stampede (Thundering Herd), Cache Avalanche, and Cache Penetration?**
   *Answer:* Cache Stampede happens when a single *hot key* expires and thousands of concurrent requests hit the database at once. Cache Avalanche happens when *many keys* expire at the exact same moment. Cache Penetration happens when requests query *non-existent keys* that are never cached, repeatedly bypassing the cache.
2. **Q: How does the XFetch algorithm prevent Cache Stampedes probabilistically?**
   *Answer:* XFetch computes a probability threshold based on computation time, remaining TTL, and a random variable. Reading threads proactively refresh the cache in the background *before* the key expires, ensuring the key is never missing for incoming requests.
3. **Q: Why is Delayed Double Deletion necessary in distributed microservices?**
   *Answer:* In concurrent environments, a read operation may fetch stale database data before a write commits, and write that stale data to Redis *after* the write deletes the cache. Delayed Double Deletion deletes the key a second time after 500ms to wipe out any concurrently written stale cache entries.
4. **Q: Why is `GenericJackson2JsonRedisSerializer` preferred over `JdkSerializationRedisSerializer`?**
   *Answer:* Java default serialization produces bulky binary payloads, cannot be inspected via `redis-cli`, is coupled to Java class package names, and suffers from remote code execution deserialization vulnerabilities. Jackson JSON produces compact, readable, cross-language JSON.
5. **Q: Why does deleting a 100MB Redis Hash key with `DEL` cause cluster latency spikes?**
   *Answer:* Redis is single-threaded for command execution. Running `DEL` on a massive key synchronously reclaims thousands of memory allocations, blocking all other client commands for hundreds of milliseconds. Solution: Use `UNLINK` (non-blocking async memory reclaim).

#### Production Incident Questions
1. **Incident:** An e-commerce flash sale crashes the database within 5 seconds of launch. Redis is healthy with 0.1% CPU. What happened?
   *Diagnosis:* Cache Stampede. The product details key expired right at sale launch. 50,000 users simultaneously missed cache and hit PostgreSQL. Fix: Implement distributed mutex locking on cache miss.
2. **Incident:** Every day at 02:00 AM, database CPU spikes to 100% and recovers at 02:15 AM. Why?
   *Diagnosis:* Cache Avalanche. A daily batch job cached items with a fixed 24-hour TTL at 02:00 AM the previous day. Fix: Add TTL jitter (`baseTtl + rand(0, 1800)`).
3. **Incident:** A malicious bot sends 500 req/sec with random UUIDs to `/api/v1/users/{uuid}`, causing DB exhaustion. How do you stop it?
   *Diagnosis:* Cache Penetration. Fix: Cache null results (`NULL_SENTINEL`) with a 60-second TTL or deploy a Bloom Filter at the edge.
4. **Incident:** An account balance updated in PostgreSQL still shows old balance in mobile app for 1 hour. Why?
   *Diagnosis:* Dual-write race condition. A concurrent reader repopulated Redis with stale data after the update deleted the cache. Fix: Use Delayed Double Deletion.
5. **Incident:** Redis memory usage is 95% and `redis-cli info` shows evictions climbing. Which eviction policy should you use?
   *Diagnosis:* For caching, use `volatile-lru` or `allkeys-lru` with explicit TTLs, rather than `noeviction` which rejects writes with `OOM command not allowed`.

#### Trick Questions
1. **Trick:** Does `@Cacheable` in Spring Boot prevent Cache Stampedes by default?
   *Answer:* No! Standard Spring `@Cacheable` is NOT thread-synchronized. You must set `@Cacheable(sync = true)` to enable local synchronization, or implement a distributed lock for multi-instance clusters.
2. **Trick:** If a key has no TTL set (persisted indefinitely), can a Cache Avalanche still occur?
   *Answer:* Yes, if the Redis server restarts or experiences memory eviction under `allkeys-lru`, causing massive simultaneous key evictions.
3. **Trick:** What is the difference between Redis `DEL` and `UNLINK`?
   *Answer:* `DEL` reclaims memory synchronously on the main event loop thread (blocking other commands for big keys). `UNLINK` unlinks the key from the keyspace synchronously in $O(1)$ and reclaims memory asynchronously in a background thread.

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
