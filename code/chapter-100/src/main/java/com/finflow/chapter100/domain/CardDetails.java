package com.finflow.chapter100.domain;

import com.fasterxml.jackson.annotation.JsonView;
import com.finflow.chapter100.correct.jackson.MaskCardPan;

public record CardDetails(
    @JsonView(Views.PublicView.class)
    String cardHolderName,

    @MaskCardPan
    @JsonView(Views.PublicView.class)
    String cardNumber,

    @JsonView(Views.PublicView.class)
    String expiryMonth,

    @JsonView(Views.PublicView.class)
    String expiryYear,

    @JsonView(Views.InternalAuditView.class)
    String cvv
) {}
