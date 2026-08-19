package com.finflow.chapter120.domain;

import java.util.UUID;

public interface PaymentIntentView {
    UUID getId();
    UUID getCustomerId();
    Long getAmountCents();
    PaymentStatus getStatus();
}
