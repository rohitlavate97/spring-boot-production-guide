package com.finflow.chapter110.domain;

public record PaymentExecutionResult(
        String paymentId,
        String status,
        String transactionRef,
        long durationMs
) {
}
