package com.finflow.chapter020.domain;

import java.time.YearMonth;
import java.util.Map;
import java.util.UUID;

public record ReportSummary(
    UUID merchantId,
    YearMonth month,
    long totalTransactions,
    long totalAmountCents,
    long successfulCount,
    long failedCount,
    Map<String, Long> statusBreakdown
) {}
