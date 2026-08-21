package com.finflow.chapter360.model;

import java.math.BigDecimal;
import java.time.Instant;

public class FeeCalculationResult {

    private String transactionId;
    private BigDecimal grossAmount;
    private BigDecimal percentageFee;
    private BigDecimal fixedFee;
    private BigDecimal totalFee;
    private BigDecimal netPayoutAmount;
    private BigDecimal appliedRatePercent;
    private String environmentTier;
    private Instant calculatedAt;

    public FeeCalculationResult() {
        this.calculatedAt = Instant.now();
    }

    public FeeCalculationResult(String transactionId, BigDecimal grossAmount, BigDecimal percentageFee,
                                BigDecimal fixedFee, BigDecimal totalFee, BigDecimal netPayoutAmount,
                                BigDecimal appliedRatePercent, String environmentTier) {
        this.transactionId = transactionId;
        this.grossAmount = grossAmount;
        this.percentageFee = percentageFee;
        this.fixedFee = fixedFee;
        this.totalFee = totalFee;
        this.netPayoutAmount = netPayoutAmount;
        this.appliedRatePercent = appliedRatePercent;
        this.environmentTier = environmentTier;
        this.calculatedAt = Instant.now();
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public BigDecimal getGrossAmount() {
        return grossAmount;
    }

    public void setGrossAmount(BigDecimal grossAmount) {
        this.grossAmount = grossAmount;
    }

    public BigDecimal getPercentageFee() {
        return percentageFee;
    }

    public void setPercentageFee(BigDecimal percentageFee) {
        this.percentageFee = percentageFee;
    }

    public BigDecimal getFixedFee() {
        return fixedFee;
    }

    public void setFixedFee(BigDecimal fixedFee) {
        this.fixedFee = fixedFee;
    }

    public BigDecimal getTotalFee() {
        return totalFee;
    }

    public void setTotalFee(BigDecimal totalFee) {
        this.totalFee = totalFee;
    }

    public BigDecimal getNetPayoutAmount() {
        return netPayoutAmount;
    }

    public void setNetPayoutAmount(BigDecimal netPayoutAmount) {
        this.netPayoutAmount = netPayoutAmount;
    }

    public BigDecimal getAppliedRatePercent() {
        return appliedRatePercent;
    }

    public void setAppliedRatePercent(BigDecimal appliedRatePercent) {
        this.appliedRatePercent = appliedRatePercent;
    }

    public String getEnvironmentTier() {
        return environmentTier;
    }

    public void setEnvironmentTier(String environmentTier) {
        this.environmentTier = environmentTier;
    }

    public Instant getCalculatedAt() {
        return calculatedAt;
    }

    public void setCalculatedAt(Instant calculatedAt) {
        this.calculatedAt = calculatedAt;
    }
}
