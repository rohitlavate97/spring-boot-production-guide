package com.finflow.troubleshooting.module01.event;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentCompletedEvent(
        String orderId,
        String customerId,
        BigDecimal amount,
        Instant timestamp
) {}
