package com.finflow.chapter200.dto;

import java.math.BigDecimal;

public record SettlementSummaryDto(
        String merchantId,
        String status,
        BigDecimal totalAmount,
        long transactionCount
) {
}
