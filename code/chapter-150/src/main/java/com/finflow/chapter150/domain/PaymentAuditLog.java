package com.finflow.chapter150.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "payment_audit_logs")
public class PaymentAuditLog {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_order_id", nullable = false)
    private PaymentOrder paymentOrder;

    @Column(name = "action_type", nullable = false, length = 64)
    private String actionType;

    @Column(name = "actor", nullable = false, length = 64)
    private String actor;

    @Column(name = "logged_at", nullable = false)
    private Instant loggedAt;

    @Column(name = "details", length = 500)
    private String details;

    protected PaymentAuditLog() {
        // JPA requirement
    }

    public PaymentAuditLog(UUID id, PaymentOrder paymentOrder, String actionType, String actor, Instant loggedAt, String details) {
        this.id = id;
        this.paymentOrder = paymentOrder;
        this.actionType = actionType;
        this.actor = actor;
        this.loggedAt = loggedAt;
        this.details = details;
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

    public String getActionType() {
        return actionType;
    }

    public String getActor() {
        return actor;
    }

    public Instant getLoggedAt() {
        return loggedAt;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentAuditLog that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
