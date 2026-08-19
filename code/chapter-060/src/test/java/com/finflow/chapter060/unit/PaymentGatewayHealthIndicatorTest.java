package com.finflow.chapter060.unit;

import com.finflow.chapter060.correct.PaymentGatewayHealthIndicator;
import com.finflow.chapter060.domain.GatewayResponse;
import com.finflow.chapter060.domain.PaymentGatewayClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentGatewayHealthIndicatorTest {

    @Test
    void reportsUpWhenClientIsHealthy() {
        PaymentGatewayClient healthyClient = new StubClient(true);
        PaymentGatewayHealthIndicator indicator = new PaymentGatewayHealthIndicator(healthyClient);
        
        Health health = indicator.health();
        
        assertThat(health.getStatus()).isEqualTo(Status.UP);
        assertThat(health.getDetails()).containsEntry("gateway", "stub-gateway");
        assertThat(health.getDetails()).containsEntry("status", "Available");
    }
    
    @Test
    void reportsDownWhenClientIsUnhealthy() {
        PaymentGatewayClient unhealthyClient = new StubClient(false);
        PaymentGatewayHealthIndicator indicator = new PaymentGatewayHealthIndicator(unhealthyClient);
        
        Health health = indicator.health();
        
        assertThat(health.getStatus()).isEqualTo(Status.DOWN);
        assertThat(health.getDetails()).containsEntry("status", "Unreachable");
    }

    private static class StubClient implements PaymentGatewayClient {
        private final boolean healthy;

        StubClient(boolean healthy) {
            this.healthy = healthy;
        }

        @Override
        public GatewayResponse charge(String paymentIntentId, long amountCents, String currency) { return null; }

        @Override
        public GatewayResponse refund(String chargeId, long amountCents) { return null; }

        @Override
        public boolean isHealthy() { return healthy; }

        @Override
        public String gatewayName() { return "stub-gateway"; }
    }
}
