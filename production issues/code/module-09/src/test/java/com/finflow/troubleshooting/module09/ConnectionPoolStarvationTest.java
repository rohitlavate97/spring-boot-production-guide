package com.finflow.troubleshooting.module09;

import com.finflow.troubleshooting.module09.service.LeakSimulationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module09Application.class)
public class ConnectionPoolStarvationTest {

    @Autowired
    private LeakSimulationService leakSimulationService;

    @Test
    void testConcurrentExecutionWithinPoolLimitsSucceeds() throws InterruptedException, ExecutionException {
        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    leakSimulationService.settlePaymentHoldingConnection(new BigDecimal("100.00"), 50);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean completed = latch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(successCount.get()).isEqualTo(3);
    }
}
