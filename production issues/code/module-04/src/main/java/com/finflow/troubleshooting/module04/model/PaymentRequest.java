package com.finflow.troubleshooting.module04.model;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRequest(
        String orderId,
        BigDecimal amount,
        String currency,
        PaymentMethod method,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant timestamp
) {}
