package com.finflow.troubleshooting.module12.dto;

public class CreditAssessmentResult {

    private String customerId;
    private int creditScore;
    private String riskCategory;
    private boolean fallbackUsed;

    public CreditAssessmentResult() {}

    public CreditAssessmentResult(String customerId, int creditScore, String riskCategory, boolean fallbackUsed) {
        this.customerId = customerId;
        this.creditScore = creditScore;
        this.riskCategory = riskCategory;
        this.fallbackUsed = fallbackUsed;
    }

    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public int getCreditScore() { return creditScore; }
    public void setCreditScore(int creditScore) { this.creditScore = creditScore; }
    public String getRiskCategory() { return riskCategory; }
    public void setRiskCategory(String riskCategory) { this.riskCategory = riskCategory; }
    public boolean isFallbackUsed() { return fallbackUsed; }
    public void setFallbackUsed(boolean fallbackUsed) { this.fallbackUsed = fallbackUsed; }
}
