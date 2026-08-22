package com.finflow.troubleshooting.module22;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SchedulerPoolStarvationTest {

    @Autowired
    private TaskScheduler taskScheduler;

    @Test
    @DisplayName("A slow scheduled job in one thread MUST NOT block another scheduled task when poolSize > 1")
    void testSchedulerMultiThreadedExecution() throws Exception {
        assertThat(taskScheduler).isInstanceOf(ThreadPoolTaskScheduler.class);
        ThreadPoolTaskScheduler threadPoolScheduler = (ThreadPoolTaskScheduler) taskScheduler;
        assertThat(threadPoolScheduler.getPoolSize()).isGreaterThanOrEqualTo(2);

        CountDownLatch slowJobStarted = new CountDownLatch(1);
        CountDownLatch slowJobHold = new CountDownLatch(1);
        CountDownLatch fastJobExecuted = new CountDownLatch(1);
        AtomicBoolean fastJobCompleted = new AtomicBoolean(false);

        // 1. Schedule slow job (blocks thread for 2 seconds)
        taskScheduler.schedule(() -> {
            slowJobStarted.countDown();
            try {
                slowJobHold.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, Instant.now());

        // Wait until slow job has acquired its thread
        assertThat(slowJobStarted.await(1, TimeUnit.SECONDS)).isTrue();

        // 2. Schedule fast job immediately
        taskScheduler.schedule(() -> {
            fastJobCompleted.set(true);
            fastJobExecuted.countDown();
        }, Instant.now());

        // Fast job MUST execute promptly on a separate worker thread without waiting for slow job!
        boolean executedPromptly = fastJobExecuted.await(1, TimeUnit.SECONDS);
        slowJobHold.countDown(); // Release slow job

        assertThat(executedPromptly).isTrue();
        assertThat(fastJobCompleted.get()).isTrue();
    }
}
