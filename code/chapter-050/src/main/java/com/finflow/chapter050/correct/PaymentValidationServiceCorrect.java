package com.finflow.chapter050.correct;

import org.springframework.stereotype.Service;

@Service
public class PaymentValidationServiceCorrect {

    // CORRECT: Use a shared component to break circular dependency
    private final PaymentValidationRules validationRules;

    public PaymentValidationServiceCorrect(PaymentValidationRules validationRules) {
        this.validationRules = validationRules;
    }

    public boolean validatePayment(String paymentIntentId) {
        return validationRules.isPaymentValid(paymentIntentId);
    }
}
