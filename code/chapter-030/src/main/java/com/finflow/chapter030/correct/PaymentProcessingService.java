package com.finflow.chapter030.correct;

import com.finflow.chapter030.domain.PaymentIntent;
import com.finflow.chapter030.domain.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class PaymentProcessingService {
    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingService.class);

    private final PaymentValidationService validationService;

    public PaymentProcessingService(PaymentValidationService validationService) {
        this.validationService = validationService;
    }

    public PaymentResult processPayment(PaymentIntent intent) {
        log.info("Processing payment: {}", intent.getId());
        
        if (!validationService.isValidForProcessing(intent)) {
            log.warn("Payment {} failed validation", intent.getId());
            return new PaymentResult(intent.getId(), "FAILED", intent.getAmountCents(), Instant.now());
        }

        return new PaymentResult(intent.getId(), "SUCCESS", intent.getAmountCents(), Instant.now());
    }
}
