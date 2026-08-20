package com.finflow.chapter250.unit;

import com.finflow.chapter250.Chapter250Application;
import com.finflow.chapter250.correct.FeeScheduleCacheServiceCorrect;
import com.finflow.chapter250.domain.MerchantFeeSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter250Application.class)
public class CacheEvictAndPutTest {

    @Autowired
    private FeeScheduleCacheServiceCorrect correctService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    public void setup() {
        correctService.resetDbQueryCount();
        if (cacheManager.getCache("merchant_fee_schedules_hardened") != null) {
            cacheManager.getCache("merchant_fee_schedules_hardened").clear();
        }
    }

    @Test
    public void testCachePut_updatesCachedEntryDirectly() {
        // Initial fetch -> DB query
        MerchantFeeSchedule schedule = correctService.getFeeSchedule("MERCHANT_PUT_TEST");
        assertThat(schedule.getVersion()).isEqualTo(1L);
        assertThat(correctService.getDbQueryCount()).isEqualTo(1);

        // Update via @CachePut -> updates cache in-place
        schedule.setFixedFee(BigDecimal.valueOf(0.50));
        correctService.updateFeeSchedule(schedule);

        // Subsequent get -> returns updated version from cache without DB query
        MerchantFeeSchedule cached = correctService.getFeeSchedule("MERCHANT_PUT_TEST");
        assertThat(cached.getVersion()).isEqualTo(2L);
        assertThat(cached.getFixedFee()).isEqualTo(BigDecimal.valueOf(0.50));
        assertThat(correctService.getDbQueryCount()).isEqualTo(1);
    }

    @Test
    public void testCacheEvict_invalidatesEntry_forcesDbRefresh() {
        // First read -> DB query
        correctService.getFeeSchedule("MERCHANT_EVICT_TEST");
        assertThat(correctService.getDbQueryCount()).isEqualTo(1);

        // Evict
        correctService.evictFeeSchedule("MERCHANT_EVICT_TEST");

        // Next read -> forces fresh DB query
        correctService.getFeeSchedule("MERCHANT_EVICT_TEST");
        assertThat(correctService.getDbQueryCount()).isEqualTo(2);
    }
}
