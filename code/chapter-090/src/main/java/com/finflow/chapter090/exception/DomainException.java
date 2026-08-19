package com.finflow.chapter090.exception;

import org.springframework.http.HttpStatus;

public abstract class DomainException extends FinFlowException {
    public DomainException(String message, ErrorCode errorCode, HttpStatus httpStatus) {
        super(message, errorCode, httpStatus, false);
    }
    
    public DomainException(String message, ErrorCode errorCode, HttpStatus httpStatus, boolean retryable) {
        super(message, errorCode, httpStatus, retryable);
    }
}
