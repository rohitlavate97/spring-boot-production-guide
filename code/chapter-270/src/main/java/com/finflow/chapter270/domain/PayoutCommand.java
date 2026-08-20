package com.finflow.chapter270.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

public class PayoutCommand implements Serializable {

    private String payoutId;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private String payoutType; // INSTANT, ACH, WIRE, POISON_PILL
    private String destinationIban;
    private String status;
    private int retryCount;
    private Instant createdAt;

    public PayoutCommand() {}

    public PayoutCommand(String payoutId, String merchantId, BigDecimal amount, String currency,
                         String payoutType, String destinationIban, String status, int retryCount, Instant createdAt) {
        this.payoutId = payoutId;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.payoutType = payoutType;
        this.destinationIban = destinationIban;
        this.status = status;
        this.retryCount = retryCount;
        this.createdAt = createdAt;
    }

    public String getPayoutId() { return payoutId; }
    public void setPayoutId(String payoutId) { this.payoutId = payoutId; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getPayoutType() { return payoutType; }
    public void setPayoutType(String payoutType) { this.payoutType = payoutType; }
    public String getDestinationIban() { return destinationIban; }
    public void setDestinationIban(String destinationIban) { this.destinationIban = destinationIban; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getRetryCount() { return retryCount; }
    public void setRetryCount(int retryCount) { this.retryCount = retryCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PayoutCommand that)) return false;
        return Objects.equals(payoutId, that.payoutId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(payoutId);
    }

    @Override
    public String toString() {
        return "PayoutCommand{" +
                "payoutId='" + payoutId + '\'' +
                ", merchantId='" + merchantId + '\'' +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", payoutType='" + payoutType + '\'' +
                ", destinationIban='" + destinationIban + '\'' +
                ", status='" + status + '\'' +
                ", retryCount=" + retryCount +
                '}';
    }
}
