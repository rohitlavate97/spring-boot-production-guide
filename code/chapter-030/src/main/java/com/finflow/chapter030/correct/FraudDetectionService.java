package com.finflow.chapter030.correct;

import com.finflow.chapter030.domain.PaymentIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FraudDetectionService {
    private static final Logger log = LoggerFactory.getLogger(FraudDetectionService.class);

    private final PaymentValidationService validationService;

    public FraudDetectionService(PaymentValidationService validationService) {
        this.validationService = validationService;
    }

    public boolean checkFraud(PaymentIntent intent) {
        log.info("Checking fraud for intent: {}", intent.getId());
        int merchantScore = validationService.getPaymentHistoryScore(intent.getMerchantId());
        return merchantScore < 50;
    }
}
