package com.finflow.chapter120.correct.specification;

import com.finflow.chapter120.domain.PaymentStatus;
import java.time.Instant;
import java.util.UUID;

public record PaymentSearchCriteria(
        UUID customerId,
        PaymentStatus status,
        String currency,
        Long minAmountCents,
        Long maxAmountCents,
        Instant createdAfter,
        Instant createdBefore
) {
}
