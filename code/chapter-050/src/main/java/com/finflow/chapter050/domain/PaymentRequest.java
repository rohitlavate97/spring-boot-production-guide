package com.finflow.chapter050.domain;

public record PaymentRequest(
    String paymentIntentId,
    String customerId, 
    long amountCents,
    String currency,
    String idempotencyKey
) {}
