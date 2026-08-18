package com.finflow.chapter010.domain;

import java.util.UUID;

public class PaymentCompletedEvent extends DomainEvent {
    private final UUID paymentIntentId;
    private final long amountCents;
    private final String currency;

    public PaymentCompletedEvent(UUID paymentIntentId, long amountCents, String currency) {
        super();
        this.paymentIntentId = paymentIntentId;
        this.amountCents = amountCents;
        this.currency = currency;
    }

    public UUID getPaymentIntentId() {
        return paymentIntentId;
    }

    public long getAmountCents() {
        return amountCents;
    }

    public String getCurrency() {
        return currency;
    }
}
