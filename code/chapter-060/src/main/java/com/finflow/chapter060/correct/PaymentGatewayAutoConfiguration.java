package com.finflow.chapter060.correct;

import com.finflow.chapter060.domain.PaymentGatewayClient;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * CORRECT IMPLEMENTATION
 * 
 * Proper Auto-Configuration class.
 * 1. Uses @AutoConfiguration (Spring Boot 3.x style).
 * 2. Uses conditions to only activate when necessary classes are present.
 * 3. Supports enabling/disabling via properties.
 * 4. Backs off if the user provides their own PaymentGatewayClient bean.
 */
@AutoConfiguration
@ConditionalOnClass(PaymentGatewayClient.class)
@ConditionalOnProperty(prefix = "finflow.payment.gateway", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(PaymentGatewayProperties.class)
public class PaymentGatewayAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public PaymentGatewayClient paymentGatewayClient(PaymentGatewayProperties properties) {
        return new DefaultPaymentGatewayClient(properties);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnClass(HealthIndicator.class)
    public PaymentGatewayHealthIndicator paymentGatewayHealthIndicator(PaymentGatewayClient client) {
        return new PaymentGatewayHealthIndicator(client);
    }
}
