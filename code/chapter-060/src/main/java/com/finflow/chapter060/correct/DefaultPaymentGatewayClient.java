package com.finflow.chapter060.correct;

import com.finflow.chapter060.domain.GatewayResponse;
import com.finflow.chapter060.domain.PaymentGatewayClient;
import java.util.UUID;

public class DefaultPaymentGatewayClient implements PaymentGatewayClient {

    private final PaymentGatewayProperties properties;

    public DefaultPaymentGatewayClient(PaymentGatewayProperties properties) {
        this.properties = properties;
    }

    @Override
    public GatewayResponse charge(String paymentIntentId, long amountCents, String currency) {
        // Simulated network call using configured timeouts
        try {
            Thread.sleep(Math.min(10, properties.getConnectTimeoutMs()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return new GatewayResponse(UUID.randomUUID().toString(), "SUCCESS", "Charged " + amountCents);
    }

    @Override
    public GatewayResponse refund(String chargeId, long amountCents) {
        return new GatewayResponse(UUID.randomUUID().toString(), "SUCCESS", "Refunded " + amountCents);
    }

    @Override
    public boolean isHealthy() {
        // Simulated health check
        return properties.getBaseUrl() != null && !properties.getBaseUrl().isBlank();
    }

    @Override
    public String gatewayName() {
        return "default-gateway";
    }
}
