package com.finflow.troubleshooting.module05.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record OrderItemRequest(
        @NotBlank(message = "Item sku must not be blank")
        String sku,

        @Min(value = 1, message = "Item quantity must be at least 1")
        int quantity,

        @NotNull(message = "Item price is required")
        @DecimalMin(value = "0.01", message = "Item price must be positive")
        BigDecimal price
) {}
