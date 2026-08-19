package com.finflow.chapter090.exception;

import org.springframework.http.HttpStatus;

public abstract class FinFlowException extends RuntimeException {
    private final ErrorCode errorCode;
    private final HttpStatus httpStatus;
    private final boolean retryable;

    public FinFlowException(String message, ErrorCode errorCode, HttpStatus httpStatus, boolean retryable) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public FinFlowException(String message, Throwable cause, ErrorCode errorCode, HttpStatus httpStatus, boolean retryable, boolean writableStackTrace) {
        super(message, cause, true, writableStackTrace);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getHttpStatus() {
        return httpStatus;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
