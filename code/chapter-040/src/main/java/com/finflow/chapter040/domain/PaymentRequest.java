package com.finflow.chapter040.domain;

import java.util.UUID;

public record PaymentRequest(UUID paymentIntentId, String idempotencyKey, long amountCents, String currency) {
}
