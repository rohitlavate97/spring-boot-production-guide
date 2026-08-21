package com.finflow.troubleshooting.module04.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PaymentMethod {
    CREDIT_CARD("CREDIT_CARD"),
    DEBIT_CARD("DEBIT_CARD"),
    CRYPTO("CRYPTO"),
    WIRE_TRANSFER("WIRE_TRANSFER");

    private final String value;

    PaymentMethod(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static PaymentMethod fromString(String text) {
        for (PaymentMethod b : PaymentMethod.values()) {
            if (b.value.equalsIgnoreCase(text) || b.name().equalsIgnoreCase(text)) {
                return b;
            }
        }
        throw new IllegalArgumentException("Unknown payment method: " + text + ". Allowed: CREDIT_CARD, DEBIT_CARD, CRYPTO, WIRE_TRANSFER");
    }
}
