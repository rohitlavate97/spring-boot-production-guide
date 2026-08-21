package com.finflow.troubleshooting.module13;

import com.finflow.troubleshooting.module13.service.VirtualThreadPinningService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module13Application.class)
public class VirtualThreadLockUnmountingTest {

    @Autowired
    private VirtualThreadPinningService pinningService;

    @Test
    void testVirtualThreadExecutesReentrantLockWithoutPinningCarrier() throws ExecutionException, InterruptedException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> future = executor.submit(() -> pinningService.executeReentrantLockTask(20));
            String result = future.get();
            assertThat(result).isEqualTo("REENTRANT_LOCK_COMPLETED");
        }
    }

    @Test
    void testVirtualThreadExecutesSynchronizedBlock() throws ExecutionException, InterruptedException {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<String> future = executor.submit(() -> pinningService.executeSynchronizedTask(20));
            String result = future.get();
            assertThat(result).isEqualTo("SYNCHRONIZED_COMPLETED");
        }
    }
}
