package com.finflow.chapter060.unit;

import com.finflow.chapter060.correct.DefaultPaymentGatewayClient;
import com.finflow.chapter060.correct.PaymentGatewayHealthIndicator;
import com.finflow.chapter060.correct.PaymentGatewayProperties;
import com.finflow.chapter060.domain.PaymentGatewayClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CustomStarterIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private PaymentGatewayClient paymentGatewayClient;

    @Autowired
    private PaymentGatewayProperties properties;

    @Test
    void clientIsDefaultPaymentGatewayClient() {
        assertThat(paymentGatewayClient).isInstanceOf(DefaultPaymentGatewayClient.class);
    }

    @Test
    void propertiesAreBoundFromYaml() {
        assertThat(properties.getBaseUrl()).isEqualTo("https://api.payment-gateway.example.com");
        assertThat(properties.getConnectTimeoutMs()).isEqualTo(3000);
        assertThat(properties.getReadTimeoutMs()).isEqualTo(5000);
        assertThat(properties.getMaxRetries()).isEqualTo(3);
    }

    @Test
    void healthIndicatorIsRegistered() {
        assertThat(context.getBean(PaymentGatewayHealthIndicator.class)).isNotNull();
    }
}
