package com.finflow.troubleshooting.module19;

import com.finflow.troubleshooting.module19.service.CacheStampedeGuardService;
import com.finflow.troubleshooting.module19.service.SimulatedDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CachePenetrationGuardTest {

    private CacheStampedeGuardService cacheGuard;
    private SimulatedDatabaseService databaseService;

    @BeforeEach
    void setUp() {
        databaseService = new SimulatedDatabaseService();
        cacheGuard = new CacheStampedeGuardService(300, 60, 60, 20, 20);
    }

    @Test
    @DisplayName("Should prevent cache penetration by caching sentinel for non-existent IDs")
    void testCachePenetrationNullSentinel() {
        String nonExistentAccountId = "ACC-NONEXISTENT-9999";
        String key = "account:" + nonExistentAccountId;

        // First query: misses cache, queries DB, DB returns null, sentinel cached
        Map<String, Object> first = cacheGuard.getOrComputeWithMutex(key, () ->
                databaseService.queryAccountFromDb(nonExistentAccountId, 0), 60);
        assertThat(first).isNull();
        assertThat(databaseService.getDbQueryCount()).isEqualTo(1);

        // Second query: hits null sentinel in cache, does NOT query DB!
        Map<String, Object> second = cacheGuard.getOrComputeWithMutex(key, () ->
                databaseService.queryAccountFromDb(nonExistentAccountId, 0), 60);
        assertThat(second).isNull();
        assertThat(databaseService.getDbQueryCount()).isEqualTo(1); // Still 1!
    }
}
