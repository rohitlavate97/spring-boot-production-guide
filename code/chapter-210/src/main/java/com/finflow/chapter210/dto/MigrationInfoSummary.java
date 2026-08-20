package com.finflow.chapter210.dto;

public record MigrationInfoSummary(
        String version,
        String description,
        String type,
        String script,
        String state,
        Integer checksum
) {
}
