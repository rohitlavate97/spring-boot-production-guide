package com.finflow.chapter250.correct;

import com.finflow.chapter250.domain.MerchantFeeSchedule;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * PRODUCTION-HARDENED IMPLEMENTATION:
 * 1. sync = true enforces mutex locking: exactly 1 thread executes database lookup on cache miss.
 * 2. @CachePut and @CacheEvict guarantee cache consistency during updates.
 */
@Service
public class FeeScheduleCacheServiceCorrect {

    private final AtomicInteger dbQueryCount = new AtomicInteger(0);

    /**
     * Cache Stampede Protected:
     * sync = true instructs the CacheManager to synchronize concurrent calls for the same key.
     * When 50 concurrent threads call this method with the same merchantId on a cache miss,
     * Thread 1 acquires the lock, queries the DB, and populates the cache.
     * Threads 2..50 wait and subsequently receive the cached value without touching the DB!
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
     * Cache Penetration Protected:
     * Caches null/empty values for non-existent entities, preventing repeated database scans.
     */
    @Cacheable(value = "null_safe_merchant_fees", key = "#merchantId", sync = true)
    public MerchantFeeSchedule getFeeScheduleNullSafe(String merchantId) {
        dbQueryCount.incrementAndGet();

        if (merchantId.startsWith("NON_EXISTENT")) {
            return null; // Stored as NullValue in Spring Cache
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

    public int getDbQueryCount() {
        return dbQueryCount.get();
    }

    public void resetDbQueryCount() {
        dbQueryCount.set(0);
    }
}
