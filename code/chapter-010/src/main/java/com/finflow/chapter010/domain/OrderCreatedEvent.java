package com.finflow.chapter010.domain;

import java.util.UUID;

public class OrderCreatedEvent extends DomainEvent {
    private final UUID orderId;
    private final UUID customerId;
    private final long totalCents;

    public OrderCreatedEvent(UUID orderId, UUID customerId, long totalCents) {
        super();
        this.orderId = orderId;
        this.customerId = customerId;
        this.totalCents = totalCents;
    }

    public UUID getOrderId() {
        return orderId;
    }

    public UUID getCustomerId() {
        return customerId;
    }

    public long getTotalCents() {
        return totalCents;
    }
}
