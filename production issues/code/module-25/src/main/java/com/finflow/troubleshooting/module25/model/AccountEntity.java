package com.finflow.troubleshooting.module25.model;

import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "accounts")
public class AccountEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Legacy column (Phase 1: Kept for backward compatibility with older microservice pods)
    @Column(name = "account_number", nullable = false, unique = true, length = 32)
    private String accountNumber;

    // Expanded new column (Phase 1: Added as nullable)
    @Column(name = "account_uuid", length = 64)
    private String accountUuid;

    @Column(name = "balance", nullable = false, precision = 15, scale = 2)
    private BigDecimal balance;

    @Column(name = "risk_tier", length = 32)
    private String riskTier;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public AccountEntity() {}

    public AccountEntity(String accountNumber, String accountUuid, BigDecimal balance, String riskTier) {
        this.accountNumber = accountNumber;
        this.accountUuid = accountUuid;
        this.balance = balance;
        this.riskTier = riskTier;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getAccountNumber() { return accountNumber; }
    public void setAccountNumber(String accountNumber) { this.accountNumber = accountNumber; }

    public String getAccountUuid() { return accountUuid; }
    public void setAccountUuid(String accountUuid) { this.accountUuid = accountUuid; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public String getRiskTier() { return riskTier; }
    public void setRiskTier(String riskTier) { this.riskTier = riskTier; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
