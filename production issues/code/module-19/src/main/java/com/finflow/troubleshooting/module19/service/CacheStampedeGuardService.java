package com.finflow.troubleshooting.module19.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

@Service
public class CacheStampedeGuardService {

    private static final Logger log = LoggerFactory.getLogger(CacheStampedeGuardService.class);
    public static final String NULL_SENTINEL = "__NULL_CACHE_SENTINEL__";

    public record CacheEntry(Object value, Instant expiresAt, long computeDurationMs) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final Map<String, CacheEntry> inMemoryCache = new ConcurrentHashMap<>();
    private final Map<String, String> inMemoryLocks = new ConcurrentHashMap<>();

    private final long baseTtlSec;
    private final long jitterMaxSec;
    private final long nullTtlSec;
    private final long mutexRetryWaitMs;
    private final int mutexMaxRetries;

    private final AtomicLong cacheHits = new AtomicLong(0);
    private final AtomicLong cacheMisses = new AtomicLong(0);
    private final AtomicLong mutexAcquiredCount = new AtomicLong(0);
    private final AtomicLong mutexContentionCount = new AtomicLong(0);
    private final AtomicLong nullHitsCount = new AtomicLong(0);

    public CacheStampedeGuardService(
            @Value("${finflow.cache.base-ttl-sec:300}") long baseTtlSec,
            @Value("${finflow.cache.jitter-max-sec:60}") long jitterMaxSec,
            @Value("${finflow.cache.null-ttl-sec:60}") long nullTtlSec,
            @Value("${finflow.cache.mutex-retry-wait-ms:50}") long mutexRetryWaitMs,
            @Value("${finflow.cache.mutex-max-retries:20}") int mutexMaxRetries
    ) {
        this.baseTtlSec = baseTtlSec;
        this.jitterMaxSec = jitterMaxSec;
        this.nullTtlSec = nullTtlSec;
        this.mutexRetryWaitMs = mutexRetryWaitMs;
        this.mutexMaxRetries = mutexMaxRetries;
    }

    /**
     * Cache-Aside with Distributed Mutex Lock (Stampede Guard):
     * Ensures only 1 thread loads data from DB on cache miss.
     */
    @SuppressWarnings("unchecked")
    public <T> T getOrComputeWithMutex(String key, Supplier<T> dbLoader, long customTtlSec) {
        // 1. Check existing cache
        CacheEntry entry = inMemoryCache.get(key);
        if (entry != null && !entry.isExpired()) {
            cacheHits.incrementAndGet();
            if (NULL_SENTINEL.equals(entry.value())) {
                nullHitsCount.incrementAndGet();
                return null;
            }
            return (T) entry.value();
        }

        cacheMisses.incrementAndGet();
        String lockKey = "lock:" + key;
        String lockOwnerId = UUID.randomUUID().toString();

        // 2. Attempt to acquire mutex lock with retries
        for (int attempt = 0; attempt < mutexMaxRetries; attempt++) {
            boolean acquired = tryAcquireLock(lockKey, lockOwnerId);
            if (acquired) {
                mutexAcquiredCount.incrementAndGet();
                try {
                    // Double check cache in case winner populated it while waiting
                    CacheEntry doubleCheck = inMemoryCache.get(key);
                    if (doubleCheck != null && !doubleCheck.isExpired()) {
                        return (T) doubleCheck.value();
                    }

                    long start = System.currentTimeMillis();
                    T loadedValue = dbLoader.get();
                    long computeTime = System.currentTimeMillis() - start;

                    long effectiveTtl = (customTtlSec > 0) ? customTtlSec : computeJitteredTtlSec();
                    if (loadedValue == null) {
                        // Cache Penetration Protection: Cache sentinel with short TTL
                        inMemoryCache.put(key, new CacheEntry(NULL_SENTINEL, Instant.now().plusSeconds(nullTtlSec), computeTime));
                    } else {
                        inMemoryCache.put(key, new CacheEntry(loadedValue, Instant.now().plusSeconds(effectiveTtl), computeTime));
                    }
                    return loadedValue;
                } finally {
                    releaseLock(lockKey, lockOwnerId);
                }
            } else {
                // Contention: Wait with backoff and re-check cache
                mutexContentionCount.incrementAndGet();
                try {
                    Thread.sleep(mutexRetryWaitMs);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException("Interrupted waiting for cache mutex lock", e);
                }

                CacheEntry retryEntry = inMemoryCache.get(key);
                if (retryEntry != null && !retryEntry.isExpired()) {
                    if (NULL_SENTINEL.equals(retryEntry.value())) return null;
                    return (T) retryEntry.value();
                }
            }
        }

        // Fallback: If lock acquisition timed out, load from DB directly as degraded fallback
        log.warn("Mutex lock timeout for key {}; falling back to direct DB loader", key);
        return dbLoader.get();
    }

    /**
     * Computes TTL with Randomized Jitter (Avalanche Guard)
     */
    public long computeJitteredTtlSec() {
        if (jitterMaxSec <= 0) return baseTtlSec;
        long jitter = ThreadLocalRandom.current().nextLong(0, jitterMaxSec + 1);
        return baseTtlSec + jitter;
    }

    /**
     * XFetch Probabilistic Early Expiration calculation:
     * - beta * computeTimeMs * ln(random(0, 1)) > remainingTtlMs
     */
    public boolean shouldRefreshEarly(CacheEntry entry, double beta) {
        if (entry == null) return true;
        long remainingTtlMs = entry.expiresAt().toEpochMilli() - System.currentTimeMillis();
        if (remainingTtlMs <= 0) return true;

        double rand = ThreadLocalRandom.current().nextDouble(0.0001, 1.0);
        double xFetchThreshold = -beta * entry.computeDurationMs() * Math.log(rand);
        return xFetchThreshold > remainingTtlMs;
    }

    public boolean tryAcquireLock(String lockKey, String ownerId) {
        return inMemoryLocks.putIfAbsent(lockKey, ownerId) == null;
    }

    public void releaseLock(String lockKey, String ownerId) {
        inMemoryLocks.remove(lockKey, ownerId);
    }

    public void clearCache() {
        inMemoryCache.clear();
        inMemoryLocks.clear();
        cacheHits.set(0);
        cacheMisses.set(0);
        mutexAcquiredCount.set(0);
        mutexContentionCount.set(0);
        nullHitsCount.set(0);
    }

    public void expireKey(String key) {
        inMemoryCache.remove(key);
    }

    public Map<String, Object> getCacheStats() {
        return Map.of(
                "cachedKeysCount", inMemoryCache.size(),
                "cacheHits", cacheHits.get(),
                "cacheMisses", cacheMisses.get(),
                "mutexAcquiredCount", mutexAcquiredCount.get(),
                "mutexContentionCount", mutexContentionCount.get(),
                "nullHitsCount", nullHitsCount.get()
        );
    }
}
