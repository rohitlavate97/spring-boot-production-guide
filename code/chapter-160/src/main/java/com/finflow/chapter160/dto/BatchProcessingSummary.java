package com.finflow.chapter160.dto;

public record BatchProcessingSummary(
        String batchId,
        int totalRecords,
        long durationMs,
        String executionStrategy
) {
}
