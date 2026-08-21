package com.finflow.chapter330.domain;

import java.io.Serializable;
import java.math.BigDecimal;

public class PaymentTraceRequest implements Serializable {

    private String paymentId;
    private String merchantId;
    private String currency;
    private BigDecimal amount;
    private String customerId;

    public PaymentTraceRequest() {}

    public PaymentTraceRequest(String paymentId, String merchantId, String currency,
                               BigDecimal amount, String customerId) {
        this.paymentId = paymentId;
        this.merchantId = merchantId;
        this.currency = currency;
        this.amount = amount;
        this.customerId = customerId;
    }

    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getMerchantId() { return merchantId; }
    public void setMerchantId(String merchantId) { this.merchantId = merchantId; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
}
