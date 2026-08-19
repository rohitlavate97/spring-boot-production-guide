package com.finflow.chapter070.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record PaymentRequest(
        @NotBlank String paymentIntentId,
        @NotNull @Min(1) Long amountCents,
        @NotBlank String currency,
        @NotBlank String paymentMethodId
) {}
