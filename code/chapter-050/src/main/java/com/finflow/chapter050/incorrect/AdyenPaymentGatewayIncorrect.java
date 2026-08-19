package com.finflow.chapter050.incorrect;

import com.finflow.chapter050.domain.PaymentGateway;
import com.finflow.chapter050.domain.PaymentRequest;
import com.finflow.chapter050.domain.PaymentResult;
import com.finflow.chapter050.domain.RefundRequest;
import com.finflow.chapter050.domain.RefundResult;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service // INCORRECT: Causes ambiguity with Stripe
public class AdyenPaymentGatewayIncorrect implements PaymentGateway {

    @Override
    public PaymentResult charge(PaymentRequest request) {
        return new PaymentResult(UUID.randomUUID().toString(), "SUCCEEDED", gatewayName(), null);
    }

    @Override
    public RefundResult refund(RefundRequest request) {
        return new RefundResult(UUID.randomUUID().toString(), "SUCCEEDED", null);
    }

    @Override
    public String gatewayName() {
        return "ADYEN";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
