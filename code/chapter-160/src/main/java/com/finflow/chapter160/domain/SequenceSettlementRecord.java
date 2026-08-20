package com.finflow.chapter160.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "seq_settlement_records")
public class SequenceSettlementRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "seq_settlement_gen")
    @SequenceGenerator(name = "seq_settlement_gen", sequenceName = "seq_settlement_id", allocationSize = 50)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "batch_id", nullable = false, length = 64)
    private String batchId;

    @Column(name = "merchant_code", nullable = false, length = 64)
    private String merchantCode;

    @Column(name = "gross_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal grossAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SettlementStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SequenceSettlementRecord() {
        // JPA requirement
    }

    public SequenceSettlementRecord(String batchId, String merchantCode, BigDecimal grossAmount,
                                    SettlementStatus status, Instant createdAt) {
        this.batchId = batchId;
        this.merchantCode = merchantCode;
        this.grossAmount = grossAmount;
        this.status = status;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }
    public String getBatchId() { return batchId; }
    public String getMerchantCode() { return merchantCode; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public SettlementStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SequenceSettlementRecord that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
