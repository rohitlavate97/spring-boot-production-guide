package com.finflow.chapter090.exception;

public enum ErrorCode {
    PAYMENT_DECLINED,
    GATEWAY_TIMEOUT,
    IDEMPOTENCY_CONFLICT,
    RESOURCE_NOT_FOUND,
    INVALID_REQUEST,
    INTERNAL_SERVER_ERROR
}
