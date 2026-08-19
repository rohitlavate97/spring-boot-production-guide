package com.finflow.chapter110.domain;

import java.time.Instant;

public record AuditRecord(
        String paymentId,
        String operation,
        String status,
        long durationMs,
        Instant timestamp
) {
}
