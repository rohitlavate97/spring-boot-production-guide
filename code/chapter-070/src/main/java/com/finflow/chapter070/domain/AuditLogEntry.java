package com.finflow.chapter070.domain;

public record AuditLogEntry(
        String traceId,
        String merchantId,
        String path,
        String method,
        String requestBody,
        Integer statusCode,
        Long durationMs
) {}
