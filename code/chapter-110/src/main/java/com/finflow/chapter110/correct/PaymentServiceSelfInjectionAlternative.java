package com.finflow.chapter110.correct;

import com.finflow.chapter110.correct.aspect.AuditPayment;
import com.finflow.chapter110.domain.PaymentExecutionRequest;
import com.finflow.chapter110.domain.PaymentExecutionResult;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class PaymentServiceSelfInjectionAlternative {

    private final ObjectProvider<PaymentServiceSelfInjectionAlternative> selfProvider;

    public PaymentServiceSelfInjectionAlternative(ObjectProvider<PaymentServiceSelfInjectionAlternative> selfProvider) {
        this.selfProvider = selfProvider;
    }

    public PaymentExecutionResult executePayment(PaymentExecutionRequest request) {
        // ALTERNATIVE FIX: Retrieve the proxy instance of this class from the container
        // and call the method on the proxy, NOT via 'this'.
        selfProvider.getObject().recordAuditInternalProxied(request);
        
        return new PaymentExecutionResult(
                request.paymentId(),
                "SUCCESS",
                UUID.randomUUID().toString(),
                42L
        );
    }

    @AuditPayment(action = "SELF_INJECTED_AUDIT")
    public void recordAuditInternalProxied(PaymentExecutionRequest request) {
        // This will be intercepted correctly because it was called via the proxy instance.
    }
}
