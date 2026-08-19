package com.finflow.chapter100.domain.events;

import java.time.Instant;

public record ChargeSucceededEvent(
    String eventId,
    Instant timestamp,
    String chargeId,
    Long amountCents,
    String currency
) implements WebhookEvent {}
