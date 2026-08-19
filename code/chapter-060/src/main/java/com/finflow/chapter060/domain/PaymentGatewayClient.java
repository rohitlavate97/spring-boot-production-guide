package com.finflow.chapter060.domain;

public interface PaymentGatewayClient {
    GatewayResponse charge(String paymentIntentId, long amountCents, String currency);
    GatewayResponse refund(String chargeId, long amountCents);
    boolean isHealthy();
    String gatewayName();
}
