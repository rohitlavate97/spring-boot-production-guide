package com.finflow.chapter180.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "merchant_wallets")
public class MerchantWallet {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "merchant_id", nullable = false, unique = true, length = 64)
    private String merchantId;

    @Column(name = "available_balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal availableBalance;

    @Column(name = "reserved_balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal reservedBalance;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected MerchantWallet() {
        // JPA requirement
    }

    public MerchantWallet(UUID id, String merchantId, BigDecimal availableBalance,
                          BigDecimal reservedBalance, String currency) {
        this.id = id;
        this.merchantId = merchantId;
        this.availableBalance = availableBalance;
        this.reservedBalance = reservedBalance;
        this.currency = currency;
    }

    public void credit(BigDecimal amount) {
        this.availableBalance = this.availableBalance.add(amount);
    }

    public void debit(BigDecimal amount) {
        if (this.availableBalance.compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds in wallet: " + merchantId);
        }
        this.availableBalance = this.availableBalance.subtract(amount);
    }

    public UUID getId() { return id; }
    public String getMerchantId() { return merchantId; }
    public BigDecimal getAvailableBalance() { return availableBalance; }
    public BigDecimal getReservedBalance() { return reservedBalance; }
    public String getCurrency() { return currency; }
    public Long getVersion() { return version; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MerchantWallet that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
