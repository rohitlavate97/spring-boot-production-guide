package com.finflow.chapter340.domain;

import java.time.Instant;

public class PaymentResponse {

    private String transactionId;
    private PaymentStatus status;
    private String gatewayReference;
    private long executionDurationMs;
    private String message;
    private String circuitBreakerState;
    private Instant timestamp;

    public PaymentResponse() {
        this.timestamp = Instant.now();
    }

    public PaymentResponse(String transactionId, PaymentStatus status, String gatewayReference,
                           long executionDurationMs, String message, String circuitBreakerState) {
        this.transactionId = transactionId;
        this.status = status;
        this.gatewayReference = gatewayReference;
        this.executionDurationMs = executionDurationMs;
        this.message = message;
        this.circuitBreakerState = circuitBreakerState;
        this.timestamp = Instant.now();
    }

    public static PaymentResponse success(String transactionId, String gatewayRef, long durationMs, String cbState) {
        return new PaymentResponse(transactionId, PaymentStatus.SUCCESS, gatewayRef, durationMs,
                "Payment successfully settled with gateway", cbState);
    }

    public static PaymentResponse fallback(String transactionId, String message, long durationMs, String cbState) {
        return new PaymentResponse(transactionId, PaymentStatus.FALLBACK_QUEUED, "FALLBACK-QUEUE-ASYNC",
                durationMs, message, cbState);
    }

    public static PaymentResponse rateLimited(String transactionId, String message, String cbState) {
        return new PaymentResponse(transactionId, PaymentStatus.RATE_LIMITED, null,
                0, message, cbState);
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public String getGatewayReference() {
        return gatewayReference;
    }

    public void setGatewayReference(String gatewayReference) {
        this.gatewayReference = gatewayReference;
    }

    public long getExecutionDurationMs() {
        return executionDurationMs;
    }

    public void setExecutionDurationMs(long executionDurationMs) {
        this.executionDurationMs = executionDurationMs;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getCircuitBreakerState() {
        return circuitBreakerState;
    }

    public void setCircuitBreakerState(String circuitBreakerState) {
        this.circuitBreakerState = circuitBreakerState;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }
}
