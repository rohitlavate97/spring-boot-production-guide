package com.finflow.troubleshooting.module07.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;

@Entity
@Table(name = "finflow_accounts")
public class AccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String accountId;

    @Column(nullable = false)
    private BigDecimal balance;

    @Version
    private Long version;

    public AccountEntity() {}

    public AccountEntity(String accountId, BigDecimal balance) {
        this.accountId = accountId;
        this.balance = balance;
    }

    public Long getId() { return id; }
    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public Long getVersion() { return version; }
}
