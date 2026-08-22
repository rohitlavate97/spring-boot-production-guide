package com.finflow.troubleshooting.module24.model;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;

public record AuditTransactionRecord(
        String transactionId,
        double amount,
        String currency,
        Instant timestampUtc,
        LocalDateTime dangerousLocalTime
) implements Serializable {

    public static AuditTransactionRecord create(String transactionId, double amount, String currency) {
        return new AuditTransactionRecord(
                transactionId,
                amount,
                currency,
                Instant.now(),
                LocalDateTime.now()
        );
    }
}
