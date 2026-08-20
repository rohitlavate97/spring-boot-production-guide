package com.finflow.chapter280.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class SettlementBatch implements Serializable {

    private String batchId;
    private String settlementDate;
    private int totalMerchantCount;
    private BigDecimal totalAmount;
    private String status;
    private String executedByPod;
    private Instant startedAt;
    private Instant completedAt;

    public SettlementBatch() {}

    public SettlementBatch(String batchId, String settlementDate, int totalMerchantCount,
                           BigDecimal totalAmount, String status, String executedByPod,
                           Instant startedAt, Instant completedAt) {
        this.batchId = batchId;
        this.settlementDate = settlementDate;
        this.totalMerchantCount = totalMerchantCount;
        this.totalAmount = totalAmount;
        this.status = status;
        this.executedByPod = executedByPod;
        this.startedAt = startedAt;
        this.completedAt = completedAt;
    }

    public String getBatchId() { return batchId; }
    public void setBatchId(String batchId) { this.batchId = batchId; }
    public String getSettlementDate() { return settlementDate; }
    public void setSettlementDate(String settlementDate) { this.settlementDate = settlementDate; }
    public int getTotalMerchantCount() { return totalMerchantCount; }
    public void setTotalMerchantCount(int totalMerchantCount) { this.totalMerchantCount = totalMerchantCount; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExecutedByPod() { return executedByPod; }
    public void setExecutedByPod(String executedByPod) { this.executedByPod = executedByPod; }
    public Instant getStartedAt() { return startedAt; }
    public void setStartedAt(Instant startedAt) { this.startedAt = startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SettlementBatch that)) return false;
        return Objects.equals(batchId, that.batchId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(batchId);
    }
}
