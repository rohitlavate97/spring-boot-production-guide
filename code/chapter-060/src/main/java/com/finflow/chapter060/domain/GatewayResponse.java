package com.finflow.chapter060.domain;

public record GatewayResponse(
    String transactionId,
    String status,
    String message
) {}
