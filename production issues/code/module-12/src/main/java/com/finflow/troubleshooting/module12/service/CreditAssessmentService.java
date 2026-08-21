package com.finflow.troubleshooting.module12.service;

import com.finflow.troubleshooting.module12.client.ExternalCreditAgencyClient;
import com.finflow.troubleshooting.module12.dto.CreditAssessmentResult;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class CreditAssessmentService {

    private static final Logger log = LoggerFactory.getLogger(CreditAssessmentService.class);

    private final ExternalCreditAgencyClient agencyClient;

    public CreditAssessmentService(ExternalCreditAgencyClient agencyClient) {
        this.agencyClient = agencyClient;
    }

    // Only @CircuitBreaker defines the fallbackMethod so failure exceptions propagate through Bulkhead and Retry to CircuitBreaker
    @CircuitBreaker(name = "creditAssessmentService", fallbackMethod = "fallbackAssessCredit")
    @Retry(name = "creditAssessmentService")
    @Bulkhead(name = "creditAssessmentService")
    public CreditAssessmentResult evaluateCredit(String customerId, boolean simulateFailure, boolean simulateTimeout) {
        return agencyClient.assessCredit(customerId, simulateFailure, simulateTimeout);
    }

    // Graceful Degradation Fallback Method
    public CreditAssessmentResult fallbackAssessCredit(String customerId, boolean simulateFailure,
                                                       boolean simulateTimeout, Throwable ex) {
        log.warn("[FALLBACK] Downstream credit agency unavailable ({}). Returning conservative fallback score.", ex.getMessage());
        return new CreditAssessmentResult(customerId, 600, "MANUAL_REVIEW_FALLBACK", true);
    }
}
