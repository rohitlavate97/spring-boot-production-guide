package com.finflow.chapter120.domain;

import java.time.Instant;
import java.util.UUID;

public record PaymentIntentSummary(
        UUID id,
        UUID customerId,
        Long amountCents,
        String currency,
        PaymentStatus status,
        Instant createdAt
) {
}
