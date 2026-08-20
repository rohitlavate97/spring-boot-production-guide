package com.finflow.chapter320.domain;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

public class PaymentTransaction implements Serializable {

    private String transactionId;
    private String merchantId;
    private String currency;
    private BigDecimal amount;
    private String paymentMethod; // CREDIT_CARD, ACH, CRYPTO
    private String status;        // SUCCESS, FAILED, DECLINED
    private long latencyMs;

    public PaymentTransaction() {}

    public PaymentTransaction(String transactionId, String merchantId, String currency,
                              BigDecimal amount, String paymentMethod, String status, long latencyMs) {
        this.transactionId = transactionId;
        this.merchantId = merchantId;
        this.currency = currency;
        this.amount = amount;
        this.paymentMethod = paymentMethod;
        this.status = status;
        this.latencyMs = latencyMs;
    }

    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public long getLatencyMs() { return latencyMs; }
    public void setLatencyMs(long latencyMs) { this.latencyMs = latencyMs; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentTransaction that)) return false;
        return Objects.equals(transactionId, that.transactionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId);
    }
}
