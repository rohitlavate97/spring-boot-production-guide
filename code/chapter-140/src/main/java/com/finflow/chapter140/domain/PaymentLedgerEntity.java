package com.finflow.chapter140.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.persistence.Version;
import jakarta.persistence.Column;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "payment_ledger",
    uniqueConstraints = @UniqueConstraint(columnNames = "entry_code")
)
public class PaymentLedgerEntity {

    @Id
    private UUID id;

    @Column(name = "entry_code", nullable = false)
    private String entryCode;

    private Long amountCents;
    private String currency;
    private String status;
    private Instant createdAt;

    @Version
    private Integer version;

    public PaymentLedgerEntity() {}

    public PaymentLedgerEntity(UUID id, String entryCode, Long amountCents, String currency, String status, Instant createdAt) {
        this.id = id;
        this.entryCode = entryCode;
        this.amountCents = amountCents;
        this.currency = currency;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    
    public String getEntryCode() { return entryCode; }
    public void setEntryCode(String entryCode) { this.entryCode = entryCode; }
    
    public Long getAmountCents() { return amountCents; }
    public void setAmountCents(Long amountCents) { this.amountCents = amountCents; }
    
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}
