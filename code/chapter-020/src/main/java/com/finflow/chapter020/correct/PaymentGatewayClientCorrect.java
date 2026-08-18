package com.finflow.chapter020.correct;

import com.finflow.chapter020.domain.PaymentRequest;
import com.finflow.chapter020.domain.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
public class PaymentGatewayClientCorrect {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayClientCorrect.class);
    
    // GOOD: Using ConcurrentHashMap for thread safety
    private final ConcurrentHashMap<String, PaymentResult> idempotencyCache = new ConcurrentHashMap<>();
    
    // GOOD: Using ReentrantLock instead of synchronized.
    // Virtual threads can unmount and yield their carrier thread when blocking on a ReentrantLock.
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public PaymentResult processPayment(PaymentRequest request) {
        String key = request.idempotencyKey();
        
        // Fast path: cache hit
        PaymentResult existing = idempotencyCache.get(key);
        if (existing != null) {
            log.info("Cache hit for idempotency key: {}", key);
            return existing;
        }

        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock(); // Virtual thread friendly blocking!
        try {
            // Double-check pattern
            existing = idempotencyCache.get(key);
            if (existing != null) {
                return existing;
            }

            log.info("Processing new payment for key: {}", key);
            
            try {
                // Simulating a blocking HTTP call to a downstream gateway.
                // Virtual thread yields instead of pinning!
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
            
            idempotencyCache.put(key, result);
            return result;
        } finally {
            lock.unlock();
            // Cleanup lock to avoid memory leak
            locks.remove(key); 
        }
    }
}
