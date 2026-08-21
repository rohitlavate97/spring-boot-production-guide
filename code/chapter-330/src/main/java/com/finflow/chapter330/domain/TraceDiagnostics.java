package com.finflow.chapter330.domain;

import java.io.Serializable;

public class TraceDiagnostics implements Serializable {

    private String traceId;
    private String spanId;
    private String baggageMerchantId;
    private String fraudCheckDecision;
    private long executionDurationMs;

    public TraceDiagnostics() {}

    public TraceDiagnostics(String traceId, String spanId, String baggageMerchantId,
                            String fraudCheckDecision, long executionDurationMs) {
        this.traceId = traceId;
        this.spanId = spanId;
        this.baggageMerchantId = baggageMerchantId;
        this.fraudCheckDecision = fraudCheckDecision;
        this.executionDurationMs = executionDurationMs;
    }

    public String getTraceId() { return traceId; }
    public void setTraceId(String traceId) { this.traceId = traceId; }
    public String getSpanId() { return spanId; }
    public void setSpanId(String spanId) { this.spanId = spanId; }
    public String getBaggageMerchantId() { return baggageMerchantId; }
    public void setBaggageMerchantId(String baggageMerchantId) { this.baggageMerchantId = baggageMerchantId; }
    public String getFraudCheckDecision() { return fraudCheckDecision; }
    public void setFraudCheckDecision(String fraudCheckDecision) { this.fraudCheckDecision = fraudCheckDecision; }
    public long getExecutionDurationMs() { return executionDurationMs; }
    public void setExecutionDurationMs(long executionDurationMs) { this.executionDurationMs = executionDurationMs; }
}
