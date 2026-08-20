package com.finflow.chapter160.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "settlement_records")
public class SettlementRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "batch_id", nullable = false, length = 64)
    private String batchId;

    @Column(name = "merchant_code", nullable = false, length = 64)
    private String merchantCode;

    @Column(name = "transaction_ref", nullable = false, length = 64)
    private String transactionRef;

    @Column(name = "gross_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal grossAmount;

    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal feeAmount;

    @Column(name = "net_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal netAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private SettlementStatus status;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Version
    @Column(name = "version")
    private Long version;

    protected SettlementRecord() {
        // JPA requirement
    }

    public SettlementRecord(UUID id, String batchId, String merchantCode, String transactionRef,
                            BigDecimal grossAmount, BigDecimal feeAmount, BigDecimal netAmount,
                            String currency, SettlementStatus status, Instant processedAt) {
        this.id = id;
        this.batchId = batchId;
        this.merchantCode = merchantCode;
        this.transactionRef = transactionRef;
        this.grossAmount = grossAmount;
        this.feeAmount = feeAmount;
        this.netAmount = netAmount;
        this.currency = currency;
        this.status = status;
        this.processedAt = processedAt;
    }

    public UUID getId() { return id; }
    public String getBatchId() { return batchId; }
    public String getMerchantCode() { return merchantCode; }
    public String getTransactionRef() { return transactionRef; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getFeeAmount() { return feeAmount; }
    public BigDecimal getNetAmount() { return netAmount; }
    public String getCurrency() { return currency; }
    public SettlementStatus getStatus() { return status; }
    public void setStatus(SettlementStatus status) { this.status = status; }
    public Instant getProcessedAt() { return processedAt; }
    public Long getVersion() { return version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SettlementRecord that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
