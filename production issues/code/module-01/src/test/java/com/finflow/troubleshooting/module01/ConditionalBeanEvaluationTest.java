package com.finflow.troubleshooting.module01;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class ConditionalBeanEvaluationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    void testConditionalBeanNotLoadedWhenPropertyDisabled() {
        contextRunner
                .withUserConfiguration(FeatureToggleConfig.class)
                .withPropertyValues("finflow.features.crypto-settlement.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.containsBean("cryptoSettlementEngine")).isFalse();
                });
    }

    @Test
    void testConditionalBeanLoadedWhenPropertyEnabled() {
        contextRunner
                .withUserConfiguration(FeatureToggleConfig.class)
                .withPropertyValues("finflow.features.crypto-settlement.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.containsBean("cryptoSettlementEngine")).isTrue();
                });
    }

    @Configuration
    static class FeatureToggleConfig {
        @Bean("cryptoSettlementEngine")
        @ConditionalOnProperty(name = "finflow.features.crypto-settlement.enabled", havingValue = "true")
        public String cryptoSettlementEngine() {
            return "ACTIVE_CRYPTO_ENGINE";
        }
    }
}
