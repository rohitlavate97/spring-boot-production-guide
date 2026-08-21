package com.finflow.troubleshooting.module02;

import com.finflow.troubleshooting.module02.config.FinFlowCoreProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class RelaxedBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void testRelaxedBindingSnakeCaseAndUppercase() {
        contextRunner
                .withPropertyValues(
                        "finflow.core.gateway_url=https://relaxed.finflow.com",
                        "finflow.core.timeout_ms=7500",
                        "finflow.core.max_retries=4",
                        "finflow.core.api_key=relaxed-key-999"
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FinFlowCoreProperties props = context.getBean(FinFlowCoreProperties.class);
                    assertThat(props.getGatewayUrl()).isEqualTo("https://relaxed.finflow.com");
                    assertThat(props.getTimeoutMs()).isEqualTo(7500);
                    assertThat(props.getMaxRetries()).isEqualTo(4);
                    assertThat(props.getApiKey()).isEqualTo("relaxed-key-999");
                });
    }

    @Configuration
    @EnableConfigurationProperties(FinFlowCoreProperties.class)
    static class TestConfig {}
}
