package com.finflow.troubleshooting.module20.model;

import java.io.Serializable;
import java.time.Instant;

public record PaymentEvent(
        String transactionId,
        String accountId,
        double amount,
        String currency,
        Instant timestamp,
        String status
) implements Serializable {

    public static PaymentEvent of(String transactionId, String accountId, double amount, String currency) {
        return new PaymentEvent(transactionId, accountId, amount, currency, Instant.now(), "PENDING");
    }
}
