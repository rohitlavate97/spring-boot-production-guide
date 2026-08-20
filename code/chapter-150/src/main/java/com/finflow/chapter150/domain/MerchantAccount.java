package com.finflow.chapter150.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "merchant_accounts")
public class MerchantAccount {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "merchant_code", nullable = false, unique = true, length = 64)
    private String merchantCode;

    @Column(name = "business_name", nullable = false, length = 128)
    private String businessName;

    @Column(name = "settlement_tier", nullable = false, length = 32)
    private String settlementTier;

    protected MerchantAccount() {
        // JPA requirement
    }

    public MerchantAccount(UUID id, String merchantCode, String businessName, String settlementTier) {
        this.id = id;
        this.merchantCode = merchantCode;
        this.businessName = businessName;
        this.settlementTier = settlementTier;
    }

    public UUID getId() {
        return id;
    }

    public String getMerchantCode() {
        return merchantCode;
    }

    public String getBusinessName() {
        return businessName;
    }

    public String getSettlementTier() {
        return settlementTier;
    }

    public void setSettlementTier(String settlementTier) {
        this.settlementTier = settlementTier;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MerchantAccount that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
