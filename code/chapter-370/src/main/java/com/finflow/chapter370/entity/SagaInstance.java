package com.finflow.chapter370.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "saga_instances")
public class SagaInstance {

    @Id
    private String sagaId;

    @Column(nullable = false, length = 64)
    private String orderId;

    @Column(nullable = false, length = 64)
    private String currentStep;

    @Column(nullable = false, length = 32)
    private String status; // STARTED, PAYMENT_AUTHORIZED, LEDGER_COMMITTED, COMPLETED, COMPENSATING_PAYMENT, COMPENSATED_FAILED

    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private Instant updatedAt;

    public SagaInstance() {
        this.sagaId = UUID.randomUUID().toString();
        this.currentStep = "START";
        this.status = "STARTED";
        this.createdAt = Instant.now();
    }

    public SagaInstance(String orderId) {
        this.sagaId = UUID.randomUUID().toString();
        this.orderId = orderId;
        this.currentStep = "START";
        this.status = "STARTED";
        this.createdAt = Instant.now();
    }

    public String getSagaId() {
        return sagaId;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public void setCurrentStep(String currentStep) {
        this.currentStep = currentStep;
        this.updatedAt = Instant.now();
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
