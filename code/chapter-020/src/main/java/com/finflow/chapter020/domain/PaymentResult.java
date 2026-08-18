package com.finflow.chapter020.domain;

import java.time.Instant;
import java.util.UUID;

public record PaymentResult(UUID chargeId, String status, long amountCents, Instant processedAt) {}
