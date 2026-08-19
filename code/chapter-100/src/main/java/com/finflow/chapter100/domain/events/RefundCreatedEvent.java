package com.finflow.chapter100.domain.events;

import java.time.Instant;

public record RefundCreatedEvent(
    String eventId,
    Instant timestamp,
    String refundId,
    String originalChargeId,
    Long amountCents,
    String reason
) implements WebhookEvent {}
