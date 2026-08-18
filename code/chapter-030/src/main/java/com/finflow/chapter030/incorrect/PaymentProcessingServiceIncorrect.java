package com.finflow.chapter030.incorrect;

import com.finflow.chapter030.domain.PaymentIntent;
import com.finflow.chapter030.domain.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;

/**
 * INCORRECT IMPLEMENTATION: Demonstrates circular dependency.
 * (Omitted @Service to avoid context startup failure by default)
 */
public class PaymentProcessingServiceIncorrect {
    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingServiceIncorrect.class);
    
    private final FraudDetectionServiceIncorrect fraudDetectionService;

    public PaymentProcessingServiceIncorrect(FraudDetectionServiceIncorrect fraudDetectionService) {
        this.fraudDetectionService = fraudDetectionService;
    }

    public PaymentResult processPayment(PaymentIntent intent) {
        log.info("Processing payment: {}", intent.getId());
        
        // Circular dependency call
        boolean isFraudulent = fraudDetectionService.checkFraud(intent);
        if (isFraudulent) {
            log.warn("Payment {} blocked due to fraud", intent.getId());
            return new PaymentResult(intent.getId(), "BLOCKED", intent.getAmountCents(), Instant.now());
        }
        
        return new PaymentResult(intent.getId(), "SUCCESS", intent.getAmountCents(), Instant.now());
    }
    
    public int getPaymentHistoryScore(java.util.UUID merchantId) {
        // Dummy logic for history score
        return 85;
    }
}
