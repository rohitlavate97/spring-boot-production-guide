package com.finflow.chapter280.unit;

import com.finflow.chapter280.Chapter280Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Instant;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter280Application.class)
public class TaskSchedulerThreadPoolConfigTest {

    @Autowired
    @Qualifier("customThreadPoolTaskScheduler")
    private ThreadPoolTaskScheduler taskScheduler;

    @Test
    public void testTaskScheduler_executesConcurrentlyWithoutHeadOfLineBlocking() throws InterruptedException {
        // getScheduledThreadPoolExecutor().getCorePoolSize() checks the configured capacity (10)
        assertThat(taskScheduler.getScheduledThreadPoolExecutor().getCorePoolSize()).isEqualTo(10);

        CountDownLatch latch = new CountDownLatch(2);
        Set<String> threadNames = ConcurrentHashMap.newKeySet();

        // Schedule two parallel tasks
        taskScheduler.schedule(() -> {
            threadNames.add(Thread.currentThread().getName());
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            } finally {
                latch.countDown();
            }
        }, Instant.now());

        taskScheduler.schedule(() -> {
            threadNames.add(Thread.currentThread().getName());
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {
            } finally {
                latch.countDown();
            }
        }, Instant.now());

        boolean completed = latch.await(5, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        // Both tasks ran concurrently on separate worker threads from the pool!
        assertThat(threadNames).hasSize(2);
        for (String name : threadNames) {
            assertThat(name).startsWith("scheduled-task-pool-");
        }
    }
}
