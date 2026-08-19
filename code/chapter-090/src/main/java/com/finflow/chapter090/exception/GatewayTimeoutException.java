package com.finflow.chapter090.exception;

import org.springframework.http.HttpStatus;

public class GatewayTimeoutException extends InfrastructureException {
    private final String gatewayName;
    private final int retryAfterSeconds;

    public GatewayTimeoutException(String gatewayName, int retryAfterSeconds) {
        super("Gateway timeout when calling: " + gatewayName, ErrorCode.GATEWAY_TIMEOUT, HttpStatus.GATEWAY_TIMEOUT, true);
        this.gatewayName = gatewayName;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getGatewayName() {
        return gatewayName;
    }

    public int getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
