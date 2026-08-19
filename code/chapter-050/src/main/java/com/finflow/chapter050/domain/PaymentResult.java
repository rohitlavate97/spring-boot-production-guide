package com.finflow.chapter050.domain;

public record PaymentResult(
    String chargeId,
    String status,
    String gatewayName,
    String failureReason
) {}
