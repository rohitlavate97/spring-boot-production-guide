package com.finflow.chapter400.model;

import java.time.Instant;
import java.util.List;

public class CanaryVerificationResult {

    private String deploymentVersion;
    private int currentTrafficWeightPercent;
    private boolean smokeTestsPassed;
    private double observedErrorRatePercent;
    private double observedP99LatencyMs;
    private boolean promotionApproved;
    private List<String> verificationLogs;
    private Instant evaluatedAt;

    public CanaryVerificationResult() {
        this.evaluatedAt = Instant.now();
    }

    public CanaryVerificationResult(String deploymentVersion, int currentTrafficWeightPercent,
                                    boolean smokeTestsPassed, double observedErrorRatePercent,
                                    double observedP99LatencyMs, boolean promotionApproved,
                                    List<String> verificationLogs) {
        this.deploymentVersion = deploymentVersion;
        this.currentTrafficWeightPercent = currentTrafficWeightPercent;
        this.smokeTestsPassed = smokeTestsPassed;
        this.observedErrorRatePercent = observedErrorRatePercent;
        this.observedP99LatencyMs = observedP99LatencyMs;
        this.promotionApproved = promotionApproved;
        this.verificationLogs = verificationLogs;
        this.evaluatedAt = Instant.now();
    }

    public String getDeploymentVersion() {
        return deploymentVersion;
    }

    public int getCurrentTrafficWeightPercent() {
        return currentTrafficWeightPercent;
    }

    public boolean isSmokeTestsPassed() {
        return smokeTestsPassed;
    }

    public double getObservedErrorRatePercent() {
        return observedErrorRatePercent;
    }

    public double getObservedP99LatencyMs() {
        return observedP99LatencyMs;
    }

    public boolean isPromotionApproved() {
        return promotionApproved;
    }

    public List<String> getVerificationLogs() {
        return verificationLogs;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }
}
