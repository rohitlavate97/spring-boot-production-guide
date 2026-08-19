package com.finflow.chapter080.domain;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record SplitAllocation(
    @NotBlank(message = "Merchant ID is required")
    String merchantId,
    
    @Min(value = 1, message = "Amount must be at least 1 cent")
    long amountCents
) {
}
