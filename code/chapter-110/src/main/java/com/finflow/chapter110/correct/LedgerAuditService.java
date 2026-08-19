package com.finflow.chapter110.correct;

import com.finflow.chapter110.correct.aspect.AuditPayment;
import com.finflow.chapter110.domain.PaymentExecutionRequest;
import org.springframework.stereotype.Service;

@Service
public class LedgerAuditService {

    @AuditPayment(action = "EXTERNAL_LEDGER_AUDIT")
    public void recordAuditExternal(PaymentExecutionRequest request) {
        // Business logic for audit logging. 
        // Called from another Spring bean, so the proxy intercepts it correctly.
    }
}
