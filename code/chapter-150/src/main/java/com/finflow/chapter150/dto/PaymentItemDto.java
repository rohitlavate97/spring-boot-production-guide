package com.finflow.chapter150.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record PaymentItemDto(
        UUID id,
        String sku,
        String itemDescription,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal feeAmount
) {
}
