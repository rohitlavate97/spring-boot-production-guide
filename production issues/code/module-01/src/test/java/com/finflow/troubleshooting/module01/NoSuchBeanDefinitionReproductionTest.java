package com.finflow.troubleshooting.module01;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class NoSuchBeanDefinitionReproductionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    void testMissingBeanThrowsNoSuchBeanDefinitionException() {
        contextRunner
                .withUserConfiguration(EmptyConfig.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    // Attempting to retrieve an unregistered bean must throw NoSuchBeanDefinitionException
                    assertThat(context.containsBean("unregisteredPaymentProcessor")).isFalse();
                });
    }

    @Configuration
    static class EmptyConfig {
        // No beans declared here
    }
}
