package com.finflow.chapter070.domain;

import java.time.Instant;

public record PaymentResponse(
        String chargeId,
        String status,
        Long authorizedAmountCents,
        Instant timestamp
) {}
