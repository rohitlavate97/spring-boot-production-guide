package com.finflow.chapter340.exception;

public class GatewayServiceUnavailableException extends RuntimeException {
    public GatewayServiceUnavailableException(String message) {
        super(message);
    }

    public GatewayServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
