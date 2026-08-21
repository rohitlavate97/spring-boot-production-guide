package com.finflow.troubleshooting.module01;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoUniqueBeanDefinitionException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import static org.assertj.core.api.Assertions.assertThat;

public class NoUniqueBeanDefinitionReproductionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    interface PaymentGateway {
        String getName();
    }

    static class StripeGateway implements PaymentGateway {
        public String getName() { return "STRIPE"; }
    }

    static class PayPalGateway implements PaymentGateway {
        public String getName() { return "PAYPAL"; }
    }

    @Test
    void testMultipleCandidatesWithoutQualifierFailsStartup() {
        contextRunner
                .withUserConfiguration(AmbiguousGatewayConfig.class, ConsumerWithoutQualifier.class)
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(NoUniqueBeanDefinitionException.class)
                            .hasMessageContaining("expected single matching bean but found 2");
                });
    }

    @Test
    void testMultipleCandidatesWithQualifierSucceeds() {
        contextRunner
                .withUserConfiguration(AmbiguousGatewayConfig.class, ConsumerWithQualifier.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ConsumerWithQualifier consumer = context.getBean(ConsumerWithQualifier.class);
                    assertThat(consumer.getGateway().getName()).isEqualTo("STRIPE");
                });
    }

    @Test
    void testPrimaryAnnotationResolvesAmbiguity() {
        contextRunner
                .withUserConfiguration(PrimaryGatewayConfig.class, ConsumerWithoutQualifier.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ConsumerWithoutQualifier consumer = context.getBean(ConsumerWithoutQualifier.class);
                    assertThat(consumer.getGateway().getName()).isEqualTo("PAYPAL");
                });
    }

    @Configuration
    static class AmbiguousGatewayConfig {
        @Bean("stripeGateway")
        public PaymentGateway stripeGateway() { return new StripeGateway(); }

        @Bean("payPalGateway")
        public PaymentGateway payPalGateway() { return new PayPalGateway(); }
    }

    @Configuration
    static class PrimaryGatewayConfig {
        @Bean("stripeGateway")
        public PaymentGateway stripeGateway() { return new StripeGateway(); }

        @Bean("payPalGateway")
        @Primary
        public PaymentGateway payPalGateway() { return new PayPalGateway(); }
    }

    @Service
    static class ConsumerWithoutQualifier {
        private final PaymentGateway gateway;
        public ConsumerWithoutQualifier(PaymentGateway gateway) { this.gateway = gateway; }
        public PaymentGateway getGateway() { return gateway; }
    }

    @Service
    static class ConsumerWithQualifier {
        private final PaymentGateway gateway;
        public ConsumerWithQualifier(@Qualifier("stripeGateway") PaymentGateway gateway) { this.gateway = gateway; }
        public PaymentGateway getGateway() { return gateway; }
    }
}
