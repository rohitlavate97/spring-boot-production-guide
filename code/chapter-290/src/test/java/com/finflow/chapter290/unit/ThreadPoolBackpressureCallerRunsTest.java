package com.finflow.chapter290.unit;

import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

public class ThreadPoolBackpressureCallerRunsTest {

    @Test
    public void testCallerRunsPolicy_providesBackpressureWithoutDroppingTasks() throws InterruptedException {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(2); // Total capacity before rejection = 4 (max) + 2 (queue) = 6
        executor.setThreadNamePrefix("backpressure-pool-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();

        int totalTasks = 15;
        CountDownLatch latch = new CountDownLatch(totalTasks);
        AtomicInteger callerThreadExecutionCount = new AtomicInteger(0);
        AtomicInteger poolThreadExecutionCount = new AtomicInteger(0);
        Set<String> executingThreads = ConcurrentHashMap.newKeySet();

        String mainThreadName = Thread.currentThread().getName();

        for (int i = 0; i < totalTasks; i++) {
            executor.submit(() -> {
                String threadName = Thread.currentThread().getName();
                executingThreads.add(threadName);

                if (threadName.equals(mainThreadName)) {
                    callerThreadExecutionCount.incrementAndGet();
                } else {
                    poolThreadExecutionCount.incrementAndGet();
                }

                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // Verifies all 15 tasks completed (zero dropped)
        assertThat(callerThreadExecutionCount.get() + poolThreadExecutionCount.get()).isEqualTo(totalTasks);
        // Verifies CallerRunsPolicy forced the submitting thread to execute overflow tasks directly
        assertThat(callerThreadExecutionCount.get()).isGreaterThan(0);
    }
}
