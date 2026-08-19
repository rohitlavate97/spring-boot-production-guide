package com.finflow.chapter050.correct;

import com.finflow.chapter050.domain.PaymentGateway;
import com.finflow.chapter050.domain.PaymentRequest;
import com.finflow.chapter050.domain.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Service
public class PaymentOrchestratorCorrect {

    private static final Logger log = LoggerFactory.getLogger(PaymentOrchestratorCorrect.class);

    // CORRECT: Dependencies are final, immutable, and injected via constructor
    private final PaymentGateway paymentGateway;
    private final IdempotencyServiceCorrect idempotencyService;

    // By default, it will inject the @Primary bean (Stripe) unless explicitly qualified
    public PaymentOrchestratorCorrect(PaymentGateway paymentGateway, 
                                      IdempotencyServiceCorrect idempotencyService) {
        this.paymentGateway = paymentGateway;
        this.idempotencyService = idempotencyService;
    }

    public PaymentResult processPayment(PaymentRequest request) {
        log.info("Processing payment for {}", request.paymentIntentId());
        
        if (idempotencyService.checkAndStore(request.idempotencyKey())) {
            return new PaymentResult(null, "DUPLICATE", paymentGateway.gatewayName(), "Already processed");
        }
        return paymentGateway.charge(request);
    }
}
