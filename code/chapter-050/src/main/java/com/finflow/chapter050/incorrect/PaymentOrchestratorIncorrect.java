package com.finflow.chapter050.incorrect;

import com.finflow.chapter050.domain.PaymentGateway;
import com.finflow.chapter050.domain.PaymentRequest;
import com.finflow.chapter050.domain.PaymentResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PaymentOrchestratorIncorrect {

    // INCORRECT: Field injection
    @Autowired
    private PaymentGateway paymentGateway; // Ambiguous: no qualifier

    // INCORRECT: Field injection makes class hard to test and hides dependencies
    @Autowired
    private IdempotencyServiceIncorrect idempotencyService;

    public PaymentResult processPayment(PaymentRequest request) {
        if (idempotencyService.checkAndStore(request.idempotencyKey())) {
            return new PaymentResult(null, "DUPLICATE", paymentGateway.gatewayName(), "Already processed");
        }
        return paymentGateway.charge(request);
    }
}
