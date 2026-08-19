package com.finflow.chapter060.unit;

import com.finflow.chapter060.correct.PaymentGatewayAutoConfiguration;
import com.finflow.chapter060.domain.PaymentGatewayClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class ConditionalAnnotationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(PaymentGatewayAutoConfiguration.class));

    @Test
    void conditionOnClassPreventsLoadingWhenClassMissing() {
        // Simulate PaymentGatewayClient not being on the classpath
        this.contextRunner.withClassLoader(new FilteredClassLoader(PaymentGatewayClient.class))
                .run((context) -> {
                    assertThat(context).doesNotHaveBean(PaymentGatewayAutoConfiguration.class);
                });
    }
    
    @Test
    void conditionalOnPropertyMatchesIfMissing() {
        // We do not specify 'finflow.payment.gateway.enabled'
        this.contextRunner.withPropertyValues("finflow.payment.gateway.base-url=https://test.com")
                .run((context) -> {
                    assertThat(context).hasSingleBean(PaymentGatewayClient.class);
                });
    }
}
