package com.finflow.chapter340.domain;

public enum PaymentStatus {
    SUCCESS,
    FAILED,
    FALLBACK_QUEUED,
    RATE_LIMITED,
    CIRCUIT_OPEN_FAST_FAIL
}
