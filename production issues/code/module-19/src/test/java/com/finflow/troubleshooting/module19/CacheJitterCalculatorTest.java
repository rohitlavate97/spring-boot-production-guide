package com.finflow.troubleshooting.module19;

import com.finflow.troubleshooting.module19.service.CacheStampedeGuardService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CacheJitterCalculatorTest {

    @Test
    @DisplayName("Should generate jittered TTLs within baseTTL and baseTTL + jitterMaxSec bounds")
    void testJitterTtlDistribution() {
        long baseTtl = 3600;
        long jitterMax = 300;
        CacheStampedeGuardService guard = new CacheStampedeGuardService(baseTtl, jitterMax, 60, 20, 20);

        Set<Long> generatedTtls = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            long ttl = guard.computeJitteredTtlSec();
            assertThat(ttl).isBetween(baseTtl, baseTtl + jitterMax);
            generatedTtls.add(ttl);
        }

        // Verify that TTLs are randomized across multiple distinct values
        assertThat(generatedTtls.size()).isGreaterThan(10);
    }
}
