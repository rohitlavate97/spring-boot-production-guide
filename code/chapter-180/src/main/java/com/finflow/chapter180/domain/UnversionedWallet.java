package com.finflow.chapter180.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "unversioned_wallets")
public class UnversionedWallet {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "merchant_id", nullable = false, unique = true, length = 64)
    private String merchantId;

    @Column(name = "available_balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal availableBalance;

    protected UnversionedWallet() {
        // JPA requirement
    }

    public UnversionedWallet(UUID id, String merchantId, BigDecimal availableBalance) {
        this.id = id;
        this.merchantId = merchantId;
        this.availableBalance = availableBalance;
    }

    public void credit(BigDecimal amount) {
        this.availableBalance = this.availableBalance.add(amount);
    }

    public void debit(BigDecimal amount) {
        this.availableBalance = this.availableBalance.subtract(amount);
    }

    public UUID getId() { return id; }
    public String getMerchantId() { return merchantId; }
    public BigDecimal getAvailableBalance() { return availableBalance; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UnversionedWallet that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
