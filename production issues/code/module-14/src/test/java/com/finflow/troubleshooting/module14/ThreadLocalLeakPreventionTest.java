package com.finflow.troubleshooting.module14;

import com.finflow.troubleshooting.module14.context.ThreadLocalContextHolder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module14Application.class)
public class ThreadLocalLeakPreventionTest {

    @AfterEach
    void tearDown() {
        ThreadLocalContextHolder.clear();
    }

    @Test
    void testAutoCloseableGuaranteesThreadLocalCleanup() {
        try (var scope = ThreadLocalContextHolder.withUser("alice_trader")) {
            assertThat(ThreadLocalContextHolder.getUser()).isEqualTo("alice_trader");
        }

        // Must be null after try-with-resources exits
        assertThat(ThreadLocalContextHolder.getUser()).isNull();
    }

    @Test
    void testThreadLocalContextDoesNotLeakAcrossThreadPoolTasks() throws ExecutionException, InterruptedException {
        ExecutorService singleThreadExecutor = Executors.newSingleThreadExecutor();

        // Task 1 sets context safely and cleans up
        singleThreadExecutor.submit(() -> {
            try (var scope = ThreadLocalContextHolder.withUser("tenant_A")) {
                assertThat(ThreadLocalContextHolder.getUser()).isEqualTo("tenant_A");
            }
        }).get();

        // Task 2 runs on the SAME reused worker thread and verifies no residual tenant_A state
        Future<String> task2Future = singleThreadExecutor.submit(ThreadLocalContextHolder::getUser);
        String task2User = task2Future.get();

        singleThreadExecutor.shutdown();
        assertThat(task2User).isNull();
    }
}
