package com.finflow.chapter050.domain;

public record RefundRequest(
    String chargeId,
    long amountCents,
    String reason
) {}
