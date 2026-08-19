package com.finflow.chapter080.domain;

public record PaymentIntentResponse(
    String intentId,
    String status,
    long amountCents,
    String currency
) {
}
