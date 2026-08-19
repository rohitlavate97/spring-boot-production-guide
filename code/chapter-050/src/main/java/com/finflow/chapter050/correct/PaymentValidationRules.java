package com.finflow.chapter050.correct;

import org.springframework.stereotype.Component;

/**
 * Extracted stateless component to share validation logic between services 
 * without introducing circular dependencies.
 */
@Component
public class PaymentValidationRules {

    public boolean isRefundEligible(String chargeId) {
        // Shared business logic to check if already fully refunded
        return true; 
    }
    
    public boolean isPaymentValid(String paymentIntentId) {
        // Shared business logic
        return true;
    }
}
