package com.finflow.chapter250.incorrect;

import com.finflow.chapter250.domain.MerchantFeeSchedule;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Missing sync = true -> vulnerable to Cache Stampede (Thundering Herd).
 * 2. Does not handle null objects -> vulnerable to Cache Penetration.
 */
@Service
public class FeeScheduleCacheServiceIncorrect {

    private final AtomicInteger dbQueryCount = new AtomicInteger(0);

    /**
     * Anti-Pattern: @Cacheable without sync=true.
     * When 50 concurrent requests hit an expired or missing key, all 50 threads
     * miss the cache simultaneously and execute the expensive database query!
     */
    @Cacheable(value = "merchant_fee_schedules", key = "#merchantId")
    public MerchantFeeSchedule getFeeScheduleUnsafe(String merchantId) {
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

    public int getDbQueryCount() {
        return dbQueryCount.get();
    }

    public void resetDbQueryCount() {
        dbQueryCount.set(0);
    }
}
