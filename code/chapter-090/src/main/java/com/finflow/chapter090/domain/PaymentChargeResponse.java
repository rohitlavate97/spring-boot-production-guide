package com.finflow.chapter090.domain;

import java.time.Instant;

public record PaymentChargeResponse(
        String chargeId,
        String status,
        Long authorizedAmountCents,
        Instant timestamp
) {}
