package com.finflow.chapter090.exception;

import org.springframework.http.HttpStatus;

public class PaymentDeclinedException extends DomainException {
    private final String declineReason;

    public PaymentDeclinedException(String declineReason) {
        super("Payment declined: " + declineReason, ErrorCode.PAYMENT_DECLINED, HttpStatus.UNPROCESSABLE_ENTITY);
        this.declineReason = declineReason;
    }

    public String getDeclineReason() {
        return declineReason;
    }
}
