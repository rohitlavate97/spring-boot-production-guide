package com.finflow.chapter020.domain;

import java.util.UUID;

public record PaymentRequest(UUID paymentIntentId, String idempotencyKey, long amountCents, String currency) {}
