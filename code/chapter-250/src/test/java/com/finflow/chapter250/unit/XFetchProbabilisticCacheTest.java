package com.finflow.chapter250.unit;

import com.finflow.chapter250.Chapter250Application;
import com.finflow.chapter250.correct.XFetchProbabilisticCacheService;
import com.finflow.chapter250.domain.MerchantFeeSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter250Application.class)
public class XFetchProbabilisticCacheTest {

    @Autowired
    private XFetchProbabilisticCacheService xFetchService;

    @BeforeEach
    public void setup() {
        xFetchService.clear();
    }

    @Test
    public void testXFetch_cachesValueAndPreventsRedundantRecomputations() {
        String key = "MERCHANT_XFETCH_1";
        long ttlMs = 10000; // 10 seconds

        // Initial compute -> loader called
        MerchantFeeSchedule s1 = xFetchService.getOrCompute(key, ttlMs, () ->
                new MerchantFeeSchedule(key, "TIER_1_ENTERPRISE", BigDecimal.valueOf(0.015), BigDecimal.valueOf(0.20), Instant.now(), 1L)
        );

        assertThat(s1).isNotNull();
        assertThat(xFetchService.getRecomputationCount()).isEqualTo(1);

        // Immediate subsequent calls within TTL -> cache hits without recomputation
        for (int i = 0; i < 5; i++) {
            MerchantFeeSchedule cached = xFetchService.getOrCompute(key, ttlMs, () ->
                    new MerchantFeeSchedule(key, "TIER_1_ENTERPRISE", BigDecimal.valueOf(0.015), BigDecimal.valueOf(0.20), Instant.now(), 1L)
            );
            assertThat(cached.getMerchantId()).isEqualTo(key);
        }

        assertThat(xFetchService.getRecomputationCount()).isEqualTo(1);
    }
}
