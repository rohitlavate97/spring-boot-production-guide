package com.finflow.chapter250.correct;

import com.finflow.chapter250.domain.MerchantFeeSchedule;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * XFetch Algorithm: Optimal Probabilistic Early Cache Expiration (Vattani et al.)
 * Eliminates Cache Stampedes by probabilistically recomputing hot cache keys before they expire.
 */
@Service
public class XFetchProbabilisticCacheService {

    private static final double BETA = 1.0; // Aggressiveness factor (>= 1.0)
    private static final Random RANDOM = new Random();

    public record XFetchEntry<T>(
            T value,
            long deltaComputeMs,
            long expiryTimestampMs
    ) {}

    private final Map<String, XFetchEntry<MerchantFeeSchedule>> cache = new ConcurrentHashMap<>();
    private final AtomicInteger recomputationCount = new AtomicInteger(0);

    /**
     * Reads from cache with XFetch probabilistic early recomputation.
     */
    public MerchantFeeSchedule getOrCompute(String key, long ttlMs, Supplier<MerchantFeeSchedule> dbLoader) {
        long now = System.currentTimeMillis();
        XFetchEntry<MerchantFeeSchedule> entry = cache.get(key);

        if (entry != null) {
            long timeRemaining = entry.expiryTimestampMs() - now;

            // XFetch formula: delta * beta * -ln(rand) > timeRemaining
            double rand = Math.max(1e-10, RANDOM.nextDouble()); // Avoid ln(0)
            double earlyExpirationThreshold = entry.deltaComputeMs() * BETA * (-Math.log(rand));

            if (timeRemaining > 0 && earlyExpirationThreshold < timeRemaining) {
                // Cache hit - early expiration condition NOT met
                return entry.value();
            }
        }

        // Cache miss OR early expiration triggered! Recompute value.
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

    public int getRecomputationCount() {
        return recomputationCount.get();
    }

    public void clear() {
        cache.clear();
        recomputationCount.set(0);
    }
}
