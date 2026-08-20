package com.finflow.chapter180.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "transfer_tasks")
public class TransferTask {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "source_merchant_id", nullable = false, length = 64)
    private String sourceMerchantId;

    @Column(name = "target_merchant_id", nullable = false, length = 64)
    private String targetMerchantId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TransferTask() {
        // JPA requirement
    }

    public TransferTask(UUID id, String sourceMerchantId, String targetMerchantId,
                        BigDecimal amount, String status, Instant createdAt) {
        this.id = id;
        this.sourceMerchantId = sourceMerchantId;
        this.targetMerchantId = targetMerchantId;
        this.amount = amount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getSourceMerchantId() { return sourceMerchantId; }
    public String getTargetMerchantId() { return targetMerchantId; }
    public BigDecimal getAmount() { return amount; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransferTask that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
