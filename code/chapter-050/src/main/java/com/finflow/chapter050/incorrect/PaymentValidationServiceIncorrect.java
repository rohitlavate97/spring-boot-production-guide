package com.finflow.chapter050.incorrect;

import org.springframework.stereotype.Service;

@Service
public class PaymentValidationServiceIncorrect {

    // INCORRECT: Circular dependency with RefundServiceIncorrect
    private final RefundServiceIncorrect refundService;

    public PaymentValidationServiceIncorrect(RefundServiceIncorrect refundService) {
        this.refundService = refundService;
    }

    public boolean validateRefundEligibility(String chargeId) {
        // Needs refundService to check if already fully refunded
        if (refundService.hasPreviousRefunds(chargeId)) {
            return false;
        }
        return true;
    }
}
