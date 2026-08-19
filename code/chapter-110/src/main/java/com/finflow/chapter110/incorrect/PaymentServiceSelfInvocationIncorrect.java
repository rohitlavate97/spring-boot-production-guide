package com.finflow.chapter110.incorrect;

import com.finflow.chapter110.correct.aspect.AuditPayment;
import com.finflow.chapter110.domain.PaymentExecutionRequest;
import com.finflow.chapter110.domain.PaymentExecutionResult;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentServiceSelfInvocationIncorrect {

    public PaymentExecutionResult executePayment(PaymentExecutionRequest request) {
        // ... some domain logic ...
        
        // BUG: Self-invocation bypasses the CGLIB proxy.
        // The @AuditPayment aspect will NOT be triggered.
        this.recordAuditInternal(request);
        
        // BUG: Self-invocation bypasses the CGLIB proxy.
        // The @Async aspect will NOT be triggered; this will execute synchronously!
        this.executeAsyncNotification(request.paymentId());
        
        return new PaymentExecutionResult(
                request.paymentId(), 
                "SUCCESS", 
                UUID.randomUUID().toString(), 
                42L
        );
    }

    @AuditPayment(action = "INTERNAL_AUDIT")
    public void recordAuditInternal(PaymentExecutionRequest request) {
        // In a real system, this might do internal DB updates.
        // We expect the aspect to intercept this, but it won't due to self-invocation.
    }

    @Async
    public void executeAsyncNotification(String paymentId) {
        // Simulating some slow network call.
        // It runs synchronously and blocks the main thread because proxy is bypassed.
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
