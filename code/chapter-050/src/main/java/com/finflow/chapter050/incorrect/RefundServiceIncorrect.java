package com.finflow.chapter050.incorrect;

import com.finflow.chapter050.domain.RefundRequest;
import org.springframework.stereotype.Service;

@Service
public class RefundServiceIncorrect {

    // INCORRECT: Circular dependency with PaymentValidationServiceIncorrect
    private final PaymentValidationServiceIncorrect validationService;

    public RefundServiceIncorrect(PaymentValidationServiceIncorrect validationService) {
        this.validationService = validationService;
    }

    public boolean processRefund(RefundRequest request) {
        if (!validationService.validateRefundEligibility(request.chargeId())) {
            return false;
        }
        return true; // proceed with refund
    }
    
    public boolean hasPreviousRefunds(String chargeId) {
        return false; // stub
    }
}
