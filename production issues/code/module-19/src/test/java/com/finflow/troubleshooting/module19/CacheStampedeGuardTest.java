package com.finflow.troubleshooting.module19;

import com.finflow.troubleshooting.module19.service.CacheStampedeGuardService;
import com.finflow.troubleshooting.module19.service.SimulatedDatabaseService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class CacheStampedeGuardTest {

    private CacheStampedeGuardService cacheGuard;
    private SimulatedDatabaseService databaseService;

    @BeforeEach
    void setUp() {
        databaseService = new SimulatedDatabaseService();
        cacheGuard = new CacheStampedeGuardService(300, 60, 60, 20, 20);
    }

    @Test
    @DisplayName("Should prevent cache stampede: 20 concurrent requests for expired key execute DB query EXACTLY ONCE")
    void testCacheStampedeMutexProtection() throws Exception {
        String key = "fx:USD_EUR";
        int concurrentThreads = 20;

        ExecutorService executor = Executors.newFixedThreadPool(concurrentThreads);
        List<Callable<Double>> tasks = new ArrayList<>();

        for (int i = 0; i < concurrentThreads; i++) {
            tasks.add(() -> cacheGuard.getOrComputeWithMutex(key, () ->
                    databaseService.queryExchangeRateFromDb("USD_EUR", 20), 300));
        }

        List<Future<Double>> futures = executor.invokeAll(tasks);
        for (Future<Double> f : futures) {
            Double rate = f.get();
            assertThat(rate).isEqualTo(0.9215);
        }
        executor.shutdown();

        // Key verification: Only 1 single thread should have hit the database!
        assertThat(databaseService.getDbQueryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Should serve subsequent reads directly from cache without incrementing DB query count")
    void testCacheHitServesFromMemory() {
        String key = "fx:USD_GBP";

        Double first = cacheGuard.getOrComputeWithMutex(key, () ->
                databaseService.queryExchangeRateFromDb("USD_GBP", 0), 300);
        assertThat(first).isEqualTo(0.7840);
        assertThat(databaseService.getDbQueryCount()).isEqualTo(1);

        Double second = cacheGuard.getOrComputeWithMutex(key, () ->
                databaseService.queryExchangeRateFromDb("USD_GBP", 0), 300);
        assertThat(second).isEqualTo(0.7840);
        assertThat(databaseService.getDbQueryCount()).isEqualTo(1); // Unchanged!
    }
}
