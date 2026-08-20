package com.finflow.chapter170.correct;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
public class FraudCheckServiceCorrect {

    public record FraudEvaluation(boolean isFraudulent, String reason) {}

    /**
     * Non-transactional rule evaluation returning domain result instead of
     * throwing unchecked exceptions across transaction boundaries.
     */
    public FraudEvaluation evaluateRisk(String customerId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.valueOf(10000.00)) > 0) {
            return new FraudEvaluation(true, "Transaction exceeds suspicious threshold: " + amount);
        }
        return new FraudEvaluation(false, "Approved");
    }
}
