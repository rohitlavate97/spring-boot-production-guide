package com.finflow.chapter250.unit;

import com.finflow.chapter250.Chapter250Application;
import com.finflow.chapter250.correct.FeeScheduleCacheServiceCorrect;
import com.finflow.chapter250.domain.MerchantFeeSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter250Application.class)
public class CachePenetrationNullSafetyTest {

    @Autowired
    private FeeScheduleCacheServiceCorrect correctService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    public void setup() {
        correctService.resetDbQueryCount();
        if (cacheManager.getCache("null_safe_merchant_fees") != null) {
            cacheManager.getCache("null_safe_merchant_fees").clear();
        }
    }

    @Test
    public void testCachePenetration_nullValueIsCached_preventsRepeatedDbLookups() {
        // First call: Entity doesn't exist -> DB query executed -> returns null
        MerchantFeeSchedule first = correctService.getFeeScheduleNullSafe("NON_EXISTENT_999");
        assertThat(first).isNull();
        assertThat(correctService.getDbQueryCount()).isEqualTo(1);

        // Second call: NullValue cached in Spring Cache -> DB is NOT queried!
        MerchantFeeSchedule second = correctService.getFeeScheduleNullSafe("NON_EXISTENT_999");
        assertThat(second).isNull();
        assertThat(correctService.getDbQueryCount()).isEqualTo(1); // Still 1!
    }
}
