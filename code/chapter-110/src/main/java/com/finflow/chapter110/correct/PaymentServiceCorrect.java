package com.finflow.chapter110.correct;

import com.finflow.chapter110.domain.PaymentExecutionRequest;
import com.finflow.chapter110.domain.PaymentExecutionResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentServiceCorrect {

    private final LedgerAuditService ledgerAuditService;
    private final PaymentNotificationService paymentNotificationService;

    public PaymentServiceCorrect(LedgerAuditService ledgerAuditService, 
                                 PaymentNotificationService paymentNotificationService) {
        this.ledgerAuditService = ledgerAuditService;
        this.paymentNotificationService = paymentNotificationService;
    }

    public PaymentExecutionResult executePayment(PaymentExecutionRequest request) {
        // CORRECT: Calling methods on injected dependencies routes through their proxies.
        ledgerAuditService.recordAuditExternal(request);
        paymentNotificationService.executeAsyncNotification(request.paymentId());
        
        return new PaymentExecutionResult(
                request.paymentId(),
                "SUCCESS",
                UUID.randomUUID().toString(),
                42L
        );
    }
}
