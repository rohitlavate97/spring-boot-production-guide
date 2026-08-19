package com.finflow.chapter110.incorrect;

import com.finflow.chapter110.correct.aspect.AuditPayment;
import com.finflow.chapter110.domain.PaymentExecutionRequest;
import org.springframework.stereotype.Service;

@Service
public class FinalMethodServiceIncorrect {

    // BUG: The method is final. Spring AOP (CGLIB) cannot override it.
    // The proxy is created, but calling this method bypasses the interceptor logic.
    @AuditPayment(action = "FINAL_METHOD_AUDIT")
    public final void finalAuditedMethod(PaymentExecutionRequest request) {
        // Business logic
    }
}
