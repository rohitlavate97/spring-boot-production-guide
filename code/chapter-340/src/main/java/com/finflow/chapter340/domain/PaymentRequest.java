package com.finflow.chapter340.domain;

import java.math.BigDecimal;

public class PaymentRequest {

    private String transactionId;
    private String merchantId;
    private BigDecimal amount;
    private String currency;
    private String paymentMethodToken;
    private boolean simulateTimeout;
    private boolean simulateServerError;

    public PaymentRequest() {
    }

    public PaymentRequest(String transactionId, String merchantId, BigDecimal amount, String currency, String paymentMethodToken) {
        this.transactionId = transactionId;
        this.merchantId = merchantId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethodToken = paymentMethodToken;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getMerchantId() {
        return merchantId;
    }

    public void setMerchantId(String merchantId) {
        this.merchantId = merchantId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getPaymentMethodToken() {
        return paymentMethodToken;
    }

    public void setPaymentMethodToken(String paymentMethodToken) {
        this.paymentMethodToken = paymentMethodToken;
    }

    public boolean isSimulateTimeout() {
        return simulateTimeout;
    }

    public void setSimulateTimeout(boolean simulateTimeout) {
        this.simulateTimeout = simulateTimeout;
    }

    public boolean isSimulateServerError() {
        return simulateServerError;
    }

    public void setSimulateServerError(boolean simulateServerError) {
        this.simulateServerError = simulateServerError;
    }
}
