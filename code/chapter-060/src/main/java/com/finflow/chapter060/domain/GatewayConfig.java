package com.finflow.chapter060.domain;

public record GatewayConfig(
    String baseUrl,
    int connectTimeoutMs,
    int readTimeoutMs,
    int maxRetries
) {}
