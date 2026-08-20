package com.finflow.chapter170.incorrect;

import com.finflow.chapter170.exception.FraudDetectedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class FraudCheckServiceIncorrect {

    /**
     * Anti-Pattern: Propagates REQUIRED, joins parent transaction, and throws RuntimeException.
     * TransactionAspectSupport marks the shared transaction as rollback-only.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void validateFraudRules(String customerId, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.valueOf(10000.00)) > 0) {
            // Throws RuntimeException -> Marks the surrounding physical transaction as rollback-only!
            throw new FraudDetectedException("Transaction exceeds suspicious threshold: " + amount);
        }
    }
}
