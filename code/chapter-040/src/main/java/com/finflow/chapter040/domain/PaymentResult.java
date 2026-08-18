package com.finflow.chapter040.domain;

import java.time.Instant;
import java.util.UUID;

public record PaymentResult(UUID chargeId, String status, long amountCents, Instant processedAt) {
}
