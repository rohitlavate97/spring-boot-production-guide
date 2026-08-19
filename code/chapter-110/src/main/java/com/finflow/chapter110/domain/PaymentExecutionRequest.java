package com.finflow.chapter110.domain;

public record PaymentExecutionRequest(
        String paymentId,
        String customerId,
        long amountCents,
        String currency
) {
}
