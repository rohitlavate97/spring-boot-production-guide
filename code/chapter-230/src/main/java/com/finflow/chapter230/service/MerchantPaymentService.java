package com.finflow.chapter230.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class MerchantPaymentService {

    public record PaymentResult(String paymentId, String merchantId, BigDecimal amount, String status) {}

    @PreAuthorize("hasAuthority('PAYMENT:WRITE') and #merchantId == authentication.principal.merchantId")
    public PaymentResult executePayment(String merchantId, BigDecimal amount) {
        return new PaymentResult(
                "PAY-" + UUID.randomUUID().toString().substring(0, 8),
                merchantId,
                amount,
                "COMPLETED"
        );
    }
}
