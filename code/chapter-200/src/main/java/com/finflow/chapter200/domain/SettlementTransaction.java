package com.finflow.chapter200.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
    name = "settlement_transactions",
    indexes = {
        @Index(name = "idx_settlement_merchant_status_created", columnList = "merchant_id, status, created_at"),
        @Index(name = "idx_settlement_gateway_ref", columnList = "gateway_reference")
    }
)
public class SettlementTransaction {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "merchant_id", nullable = false, length = 64)
    private String merchantId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "gateway_reference", length = 64)
    private String gatewayReference;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "metadata_json", length = 1024)
    private String metadataJson;

    protected SettlementTransaction() {
        // JPA requirement
    }

    public SettlementTransaction(UUID id, String merchantId, String status, BigDecimal amount,
                                 String currency, String gatewayReference, Instant createdAt, String metadataJson) {
        this.id = id;
        this.merchantId = merchantId;
        this.status = status;
        this.amount = amount;
        this.currency = currency;
        this.gatewayReference = gatewayReference;
        this.createdAt = createdAt;
        this.metadataJson = metadataJson;
    }

    public UUID getId() { return id; }
    public String getMerchantId() { return merchantId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getGatewayReference() { return gatewayReference; }
    public Instant getCreatedAt() { return createdAt; }
    public String getMetadataJson() { return metadataJson; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SettlementTransaction that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
