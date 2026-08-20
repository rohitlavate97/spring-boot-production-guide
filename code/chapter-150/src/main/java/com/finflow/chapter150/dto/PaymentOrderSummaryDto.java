package com.finflow.chapter150.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PaymentOrderSummaryDto(
        UUID id,
        String orderNumber,
        String merchantCode,
        String merchantBusinessName,
        BigDecimal totalAmount,
        String currency,
        String status,
        Instant createdAt,
        List<PaymentItemDto> items
) {
}
