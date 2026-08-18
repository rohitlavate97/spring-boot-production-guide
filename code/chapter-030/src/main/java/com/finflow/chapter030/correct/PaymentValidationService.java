package com.finflow.chapter030.correct;

import com.finflow.chapter030.domain.PaymentIntent;
import com.finflow.chapter030.repository.PaymentIntentRepository;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Extracted service that contains shared logic.
 * Both FraudDetectionService and PaymentProcessingService can depend on this,
 * eliminating the circular dependency between them.
 */
@Service
public class PaymentValidationService {

    private final PaymentIntentRepository repository;

    public PaymentValidationService(PaymentIntentRepository repository) {
        this.repository = repository;
    }

    public int getPaymentHistoryScore(UUID merchantId) {
        // Query the repository for historical data to calculate score
        long count = repository.count();
        return count > 0 ? 85 : 45; // dummy logic
    }
    
    public boolean isValidForProcessing(PaymentIntent intent) {
        return intent.getAmountCents() > 0;
    }
}
