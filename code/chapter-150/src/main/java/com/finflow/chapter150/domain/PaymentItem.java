package com.finflow.chapter150.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "payment_items")
public class PaymentItem {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id", nullable = false)
    private PaymentOrder paymentOrder;

    @Column(name = "sku", nullable = false, length = 64)
    private String sku;

    @Column(name = "item_description", nullable = false, length = 255)
    private String itemDescription;

    @Column(name = "unit_price", nullable = false, precision = 18, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false)
    private int quantity;

    @Column(name = "fee_amount", nullable = false, precision = 18, scale = 4)
    private BigDecimal feeAmount;

    protected PaymentItem() {
        // JPA requirement
    }

    public PaymentItem(UUID id, PaymentOrder paymentOrder, String sku, String itemDescription,
                       BigDecimal unitPrice, int quantity, BigDecimal feeAmount) {
        this.id = id;
        this.paymentOrder = paymentOrder;
        this.sku = sku;
        this.itemDescription = itemDescription;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.feeAmount = feeAmount;
    }

    public UUID getId() {
        return id;
    }

    public PaymentOrder getPaymentOrder() {
        return paymentOrder;
    }

    public void setPaymentOrder(PaymentOrder paymentOrder) {
        this.paymentOrder = paymentOrder;
    }

    public String getSku() {
        return sku;
    }

    public String getItemDescription() {
        return itemDescription;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getFeeAmount() {
        return feeAmount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentItem that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
