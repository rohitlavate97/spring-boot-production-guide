package com.finflow.chapter030.incorrect;

import com.finflow.chapter030.domain.PaymentIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * INCORRECT IMPLEMENTATION: Demonstrates circular dependency.
 */
public class FraudDetectionServiceIncorrect {
    private static final Logger log = LoggerFactory.getLogger(FraudDetectionServiceIncorrect.class);

    private final PaymentProcessingServiceIncorrect paymentProcessingService;

    public FraudDetectionServiceIncorrect(PaymentProcessingServiceIncorrect paymentProcessingService) {
        this.paymentProcessingService = paymentProcessingService;
    }

    public boolean checkFraud(PaymentIntent intent) {
        log.info("Checking fraud for intent: {}", intent.getId());
        
        // Circular dependency call
        int merchantScore = paymentProcessingService.getPaymentHistoryScore(intent.getMerchantId());
        
        return merchantScore < 50;
    }
}
