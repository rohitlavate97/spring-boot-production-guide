package com.finflow.chapter050.correct;

import com.finflow.chapter050.domain.RefundRequest;
import org.springframework.stereotype.Service;

@Service
public class RefundServiceCorrect {

    // CORRECT: Use a shared component to break circular dependency
    private final PaymentValidationRules validationRules;

    public RefundServiceCorrect(PaymentValidationRules validationRules) {
        this.validationRules = validationRules;
    }

    public boolean processRefund(RefundRequest request) {
        if (!validationRules.isRefundEligible(request.chargeId())) {
            return false;
        }
        return true; 
    }
}
