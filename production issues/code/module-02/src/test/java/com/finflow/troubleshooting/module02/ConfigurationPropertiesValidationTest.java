package com.finflow.troubleshooting.module02;

import com.finflow.troubleshooting.module02.config.FinFlowCoreProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationPropertiesBindException;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class ConfigurationPropertiesValidationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void testInvalidTimeoutFailsValidationOnStartup() {
        contextRunner
                .withPropertyValues(
                        "finflow.core.gateway-url=https://api.finflow.com",
                        "finflow.core.timeout-ms=25", // Below minimum of 100ms
                        "finflow.core.max-retries=3",
                        "finflow.core.api-key=test-key"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(org.springframework.boot.context.properties.bind.validation.BindValidationException.class);
                });
    }

    @Test
    void testBlankApiKeyFailsValidationOnStartup() {
        contextRunner
                .withPropertyValues(
                        "finflow.core.gateway-url=https://api.finflow.com",
                        "finflow.core.timeout-ms=5000",
                        "finflow.core.max-retries=3",
                        "finflow.core.api-key=" // Blank API Key
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(org.springframework.boot.context.properties.bind.validation.BindValidationException.class);
                });
    }

    @Configuration
    @EnableConfigurationProperties(FinFlowCoreProperties.class)
    static class TestConfig {}
}
