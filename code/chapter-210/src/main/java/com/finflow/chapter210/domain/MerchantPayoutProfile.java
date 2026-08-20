package com.finflow.chapter210.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "merchant_payout_profiles")
public class MerchantPayoutProfile {

    @Id
    @Column(name = "id", nullable = false, length = 36)
    private String id;

    @Column(name = "merchant_id", nullable = false, unique = true, length = 64)
    private String merchantId;

    @Column(name = "payout_currency", nullable = false, length = 3)
    private String payoutCurrency;

    @Column(name = "legacy_bank_account", length = 64)
    private String legacyBankAccount;

    @Column(name = "iban", length = 34)
    private String iban;

    @Column(name = "swift_routing_code", length = 11)
    private String swiftRoutingCode;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MerchantPayoutProfile() {
        // JPA requirement
    }

    public MerchantPayoutProfile(String id, String merchantId, String payoutCurrency,
                                 String legacyBankAccount, String iban, String swiftRoutingCode,
                                 String status, Instant createdAt) {
        this.id = id;
        this.merchantId = merchantId;
        this.payoutCurrency = payoutCurrency;
        this.legacyBankAccount = legacyBankAccount;
        this.iban = iban;
        this.swiftRoutingCode = swiftRoutingCode;
        this.status = status;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getMerchantId() { return merchantId; }
    public String getPayoutCurrency() { return payoutCurrency; }
    public String getLegacyBankAccount() { return legacyBankAccount; }
    public void setLegacyBankAccount(String legacyBankAccount) { this.legacyBankAccount = legacyBankAccount; }
    public String getIban() { return iban; }
    public void setIban(String iban) { this.iban = iban; }
    public String getSwiftRoutingCode() { return swiftRoutingCode; }
    public void setSwiftRoutingCode(String swiftRoutingCode) { this.swiftRoutingCode = swiftRoutingCode; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MerchantPayoutProfile that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
