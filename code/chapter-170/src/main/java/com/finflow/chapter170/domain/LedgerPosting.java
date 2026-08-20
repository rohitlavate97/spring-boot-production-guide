package com.finflow.chapter170.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "ledger_postings")
public class LedgerPosting {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "transaction_ref", nullable = false, length = 64)
    private String transactionRef;

    @Column(name = "account_id", nullable = false, length = 64)
    private String accountId;

    @Column(name = "amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction; // DEBIT or CREDIT

    @Column(name = "posted_at", nullable = false)
    private Instant postedAt;

    protected LedgerPosting() {
        // JPA requirement
    }

    public LedgerPosting(UUID id, String transactionRef, String accountId,
                         BigDecimal amount, String direction, Instant postedAt) {
        this.id = id;
        this.transactionRef = transactionRef;
        this.accountId = accountId;
        this.amount = amount;
        this.direction = direction;
        this.postedAt = postedAt;
    }

    public UUID getId() { return id; }
    public String getTransactionRef() { return transactionRef; }
    public String getAccountId() { return accountId; }
    public BigDecimal getAmount() { return amount; }
    public String getDirection() { return direction; }
    public Instant getPostedAt() { return postedAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LedgerPosting that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
