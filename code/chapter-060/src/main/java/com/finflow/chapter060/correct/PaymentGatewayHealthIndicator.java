package com.finflow.chapter060.correct;

import com.finflow.chapter060.domain.PaymentGatewayClient;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;

public class PaymentGatewayHealthIndicator extends AbstractHealthIndicator {

    private final PaymentGatewayClient client;

    public PaymentGatewayHealthIndicator(PaymentGatewayClient client) {
        this.client = client;
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) throws Exception {
        if (client.isHealthy()) {
            builder.up()
                   .withDetail("gateway", client.gatewayName())
                   .withDetail("status", "Available");
        } else {
            builder.down()
                   .withDetail("gateway", client.gatewayName())
                   .withDetail("status", "Unreachable");
        }
    }
}
