package com.finflow.chapter250.unit;

import com.finflow.chapter250.Chapter250Application;
import com.finflow.chapter250.correct.FeeScheduleCacheServiceCorrect;
import com.finflow.chapter250.incorrect.FeeScheduleCacheServiceIncorrect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter250Application.class)
public class CacheStampedeConcurrencyTest {

    @Autowired
    private FeeScheduleCacheServiceIncorrect incorrectService;

    @Autowired
    private FeeScheduleCacheServiceCorrect correctService;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    public void setup() {
        incorrectService.resetDbQueryCount();
        correctService.resetDbQueryCount();
        if (cacheManager.getCache("merchant_fee_schedules") != null) {
            cacheManager.getCache("merchant_fee_schedules").clear();
        }
        if (cacheManager.getCache("merchant_fee_schedules_hardened") != null) {
            cacheManager.getCache("merchant_fee_schedules_hardened").clear();
        }
    }

    @Test
    public void testStampede_withoutSync_triggersMultipleDatabaseHits() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    incorrectService.getFeeScheduleUnsafe("MERCHANT_STAMPEDE_TEST");
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Without sync=true, multiple threads miss the cache and hit the database concurrently!
        assertThat(incorrectService.getDbQueryCount()).isGreaterThan(1);
    }

    @Test
    public void testStampedeProtected_withSync_triggersExactlyOneDatabaseHit() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    correctService.getFeeSchedule("MERCHANT_STAMPEDE_PROTECTED");
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // Release all threads simultaneously
        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // With sync=true, exactly 1 thread executes DB query; remaining 9 wait and read cache!
        assertThat(correctService.getDbQueryCount()).isEqualTo(1);
    }
}
