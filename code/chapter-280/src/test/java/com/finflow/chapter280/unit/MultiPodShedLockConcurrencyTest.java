package com.finflow.chapter280.unit;

import com.finflow.chapter280.Chapter280Application;
import com.finflow.chapter280.correct.MultiPodShedLockSimulator;
import com.finflow.chapter280.incorrect.SettlementSchedulerServiceIncorrect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter280Application.class)
public class MultiPodShedLockConcurrencyTest {

    @Autowired
    private MultiPodShedLockSimulator shedLockSimulator;

    @Autowired
    private SettlementSchedulerServiceIncorrect incorrectService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setup() {
        shedLockSimulator.reset();
        incorrectService.reset();
        jdbcTemplate.update("DELETE FROM shedlock");
    }

    @Test
    public void testMultiPodExecution_withoutLock_causesDuplicateExecutions() throws InterruptedException {
        int podCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(podCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(podCount);

        for (int i = 0; i < podCount; i++) {
            final String podName = "pod-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    incorrectService.executeUnlockedCronAcrossPods(podName);
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // Without distributed locking, all 5 pods execute the job!
        assertThat(incorrectService.getDuplicateExecutionCount()).isEqualTo(5);
    }

    @Test
    public void testMultiPodExecution_withShedLock_allowsExactlyOnePodToExecute() throws InterruptedException {
        int podCount = 5;
        ExecutorService executor = Executors.newFixedThreadPool(podCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(podCount);
        AtomicInteger executedPodsCount = new AtomicInteger(0);

        for (int i = 0; i < podCount; i++) {
            final String podName = "pod-" + i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    boolean executed = shedLockSimulator.executeAsPod(
                            podName,
                            "dailySettlementBatchTest",
                            Duration.ofMinutes(15),
                            Duration.ofSeconds(10)
                    );
                    if (executed) {
                        executedPodsCount.incrementAndGet();
                    }
                } catch (Exception ignored) {
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        finishLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // With ShedLock, exactly ONE pod acquires the lock and executes!
        assertThat(shedLockSimulator.getSuccessCount()).isEqualTo(1);
    }
}
