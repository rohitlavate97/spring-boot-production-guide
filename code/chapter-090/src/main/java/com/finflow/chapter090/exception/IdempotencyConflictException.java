package com.finflow.chapter090.exception;

import org.springframework.http.HttpStatus;

public class IdempotencyConflictException extends DomainException {
    private final String idempotencyKey;

    public IdempotencyConflictException(String idempotencyKey) {
        super("Idempotency key conflict: " + idempotencyKey, ErrorCode.IDEMPOTENCY_CONFLICT, HttpStatus.CONFLICT);
        this.idempotencyKey = idempotencyKey;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }
}
