package com.finflow.troubleshooting.module02;

import com.finflow.troubleshooting.module02.config.FinFlowCoreProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

public class PropertyPrecedenceHierarchyTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void testProfilePropertiesOverrideDefaultProperties() {
        contextRunner
                .withPropertyValues(
                        "finflow.core.gateway-url=https://default.finflow.com",
                        "finflow.core.timeout-ms=3000",
                        "finflow.core.max-retries=1",
                        "finflow.core.api-key=test-key-1"
                )
                .withPropertyValues(
                        "finflow.core.timeout-ms=7500" // Overriding higher-precedence property source
                )
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    FinFlowCoreProperties props = context.getBean(FinFlowCoreProperties.class);
                    assertThat(props.getTimeoutMs()).isEqualTo(7500);
                });
    }

    @Configuration
    @EnableConfigurationProperties(FinFlowCoreProperties.class)
    static class TestConfig {}
}
