package com.finflow.chapter160.dto;

import java.math.BigDecimal;

public record SettlementIngestItem(
        String merchantCode,
        String transactionRef,
        BigDecimal grossAmount,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        String currency
) {
}
