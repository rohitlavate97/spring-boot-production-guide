package com.finflow.chapter100.domain;

import com.fasterxml.jackson.annotation.JsonView;
import java.time.Instant;

public record PaymentIntent(
    @JsonView(Views.PublicView.class)
    String intentId,

    @JsonView(Views.PublicView.class)
    Long amountCents,

    @JsonView(Views.PublicView.class)
    String currency,

    @JsonView(Views.PublicView.class)
    Instant createdAt,

    @JsonView(Views.PublicView.class)
    CardDetails cardDetails
) {}
