package com.finflow.chapter020.incorrect;

import com.finflow.chapter020.domain.PaymentRequest;
import com.finflow.chapter020.domain.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class PaymentGatewayClientIncorrect {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayClientIncorrect.class);
    
    // BAD: Unbounded cache using plain HashMap. Not thread-safe without synchronization.
    private final Map<String, PaymentResult> idempotencyCache = new HashMap<>();

    // BAD: Using synchronized method for deduplication.
    // When virtual threads block inside synchronized methods (due to I/O), they "pin" 
    // the underlying carrier thread, defeating the purpose of virtual threads.
    public synchronized PaymentResult processPayment(PaymentRequest request) {
        if (idempotencyCache.containsKey(request.idempotencyKey())) {
            log.info("Cache hit for idempotency key: {}", request.idempotencyKey());
            return idempotencyCache.get(request.idempotencyKey());
        }

        log.info("Processing new payment for key: {}", request.idempotencyKey());
        
        try {
            // Simulating a blocking HTTP call to a downstream gateway (e.g. Stripe)
            // Virtual thread gets pinned here because it holds the monitor lock.
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Payment processing interrupted", e);
        }

        PaymentResult result = new PaymentResult(
                UUID.randomUUID(), 
                "COMPLETED", 
                request.amountCents(), 
                Instant.now()
        );
        
        idempotencyCache.put(request.idempotencyKey(), result);
        return result;
    }
}
