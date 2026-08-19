package com.finflow.chapter090.exception;

import org.springframework.http.HttpStatus;

public abstract class InfrastructureException extends FinFlowException {
    public InfrastructureException(String message, ErrorCode errorCode, HttpStatus httpStatus, boolean retryable) {
        super(message, errorCode, httpStatus, retryable);
    }

    public InfrastructureException(String message, Throwable cause, ErrorCode errorCode, HttpStatus httpStatus, boolean retryable) {
        super(message, cause, errorCode, httpStatus, retryable, true);
    }
}
