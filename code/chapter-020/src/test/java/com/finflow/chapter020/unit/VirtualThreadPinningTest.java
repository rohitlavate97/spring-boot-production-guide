package com.finflow.chapter020.unit;

import com.finflow.chapter020.correct.PaymentGatewayClientCorrect;
import com.finflow.chapter020.domain.PaymentRequest;
import com.finflow.chapter020.incorrect.PaymentGatewayClientIncorrect;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

class VirtualThreadPinningTest {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadPinningTest.class);
    
    @Test
    void compareSynchronizedVsReentrantLock() throws InterruptedException {
        int threadCount = 10; // Reduced for unit test execution speed
        
        PaymentGatewayClientIncorrect incorrectClient = new PaymentGatewayClientIncorrect();
        PaymentGatewayClientCorrect correctClient = new PaymentGatewayClientCorrect();
        
        List<PaymentRequest> requests = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            requests.add(new PaymentRequest(UUID.randomUUID(), "idempotency-key-" + i, 1000L, "USD"));
        }

        // Test Incorrect (Synchronized)
        long incorrectDuration = measureThroughput(threadCount, requests, req -> incorrectClient.processPayment(req));
        log.info("Synchronized (Incorrect) execution time: {} ms", incorrectDuration);

        // Test Correct (ReentrantLock)
        long correctDuration = measureThroughput(threadCount, requests, req -> correctClient.processPayment(req));
        log.info("ReentrantLock (Correct) execution time: {} ms", correctDuration);
        
        // Correct should ideally be faster because it doesn't pin carrier threads, 
        // allowing all virtual threads to sleep concurrently.
        // However, this depends on system load and number of processors. 
        // We log the result to demonstrate the concept.
        assertTrue(correctDuration <= incorrectDuration + 200, 
            "ReentrantLock implementation should not be significantly slower than synchronized");
    }
    
    private long measureThroughput(int threadCount, List<PaymentRequest> requests, java.util.function.Consumer<PaymentRequest> action) throws InterruptedException {
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            CountDownLatch latch = new CountDownLatch(threadCount);
            
            long start = System.currentTimeMillis();
            
            for (int i = 0; i < threadCount; i++) {
                final int index = i;
                executor.submit(() -> {
                    try {
                        action.accept(requests.get(index));
                    } finally {
                        latch.countDown();
                    }
                });
            }
            
            latch.await(10, TimeUnit.SECONDS);
            return System.currentTimeMillis() - start;
        }
    }
}
