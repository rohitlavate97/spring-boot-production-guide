package com.finflow.chapter060.unit;

import com.finflow.chapter060.correct.DefaultPaymentGatewayClient;
import com.finflow.chapter060.correct.PaymentGatewayAutoConfiguration;
import com.finflow.chapter060.correct.PaymentGatewayProperties;
import com.finflow.chapter060.domain.PaymentGatewayClient;
import com.finflow.chapter060.domain.GatewayResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

class AutoConfigurationDiscoveryTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PaymentGatewayAutoConfiguration.class));

    @Test
    void autoConfigurationCreatesDefaultClientWhenNotProvided() {
        this.contextRunner.withPropertyValues("finflow.payment.gateway.base-url=https://test.com")
                .run((context) -> {
            assertThat(context).hasSingleBean(PaymentGatewayClient.class);
            assertThat(context).getBean(PaymentGatewayClient.class).isInstanceOf(DefaultPaymentGatewayClient.class);
        });
    }

    @Test
    void autoConfigurationBacksOffWhenUserProvidesBean() {
        this.contextRunner.withUserConfiguration(UserConfiguration.class)
                .withPropertyValues("finflow.payment.gateway.base-url=https://test.com")
                .run((context) -> {
            assertThat(context).hasSingleBean(PaymentGatewayClient.class);
            assertThat(context).getBean("customClient").isNotNull();
            assertThat(context).getBean(PaymentGatewayClient.class).isNotInstanceOf(DefaultPaymentGatewayClient.class);
        });
    }

    @Test
    void autoConfigurationDisabledWhenPropertySetToFalse() {
        this.contextRunner.withPropertyValues("finflow.payment.gateway.enabled=false")
                .run((context) -> {
            assertThat(context).doesNotHaveBean(PaymentGatewayClient.class);
        });
    }

    @Test
    void propertiesAreBoundCorrectly() {
        this.contextRunner.withPropertyValues(
                "finflow.payment.gateway.base-url=https://custom.com",
                "finflow.payment.gateway.connect-timeout-ms=8000"
        ).run((context) -> {
            PaymentGatewayProperties properties = context.getBean(PaymentGatewayProperties.class);
            assertThat(properties.getBaseUrl()).isEqualTo("https://custom.com");
            assertThat(properties.getConnectTimeoutMs()).isEqualTo(8000);
        });
    }

    static class UserConfiguration {
        @Bean
        public PaymentGatewayClient customClient() {
            return new PaymentGatewayClient() {
                @Override
                public GatewayResponse charge(String paymentIntentId, long amountCents, String currency) {
                    return null;
                }

                @Override
                public GatewayResponse refund(String chargeId, long amountCents) {
                    return null;
                }

                @Override
                public boolean isHealthy() {
                    return true;
                }

                @Override
                public String gatewayName() {
                    return "custom";
                }
            };
        }
    }
}
