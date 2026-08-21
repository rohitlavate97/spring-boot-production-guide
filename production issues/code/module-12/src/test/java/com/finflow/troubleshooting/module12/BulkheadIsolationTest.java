package com.finflow.troubleshooting.module12;

import com.finflow.troubleshooting.module12.dto.CreditAssessmentResult;
import com.finflow.troubleshooting.module12.service.CreditAssessmentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module12Application.class)
public class BulkheadIsolationTest {

    @Autowired
    private CreditAssessmentService assessmentService;

    @Test
    void testBulkheadHandlesConcurrentRequestsAndExecutesGracefully() throws InterruptedException {
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger completedCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    CreditAssessmentResult result = assessmentService.evaluateCredit("CUST-BULKHEAD-" + index, false, false);
                    if (result != null) {
                        completedCount.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        boolean finished = latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(completedCount.get()).isEqualTo(threadCount);
    }
}
