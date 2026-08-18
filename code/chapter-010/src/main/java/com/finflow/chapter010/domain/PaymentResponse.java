package com.finflow.chapter010.domain;

import com.fasterxml.jackson.annotation.JsonFormat;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
    UUID paymentIntentId,
    long amountCents,
    String currency,
    String status,
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", timezone = "UTC") Instant createdAt
) {}
