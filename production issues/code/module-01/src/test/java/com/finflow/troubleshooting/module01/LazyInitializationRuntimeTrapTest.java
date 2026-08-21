package com.finflow.troubleshooting.module01;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class LazyInitializationRuntimeTrapTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    void testLazyInitializationDelaysFailureToRuntimeAccess() {
        contextRunner
                .withUserConfiguration(BrokenLazyBeanConfig.class)
                .withPropertyValues("spring.main.lazy-initialization=true")
                .run(context -> {
                    // 1. Startup succeeds even though the bean constructor is broken!
                    assertThat(context).hasNotFailed();

                    // 2. Failure occurs at RUNTIME when a user request triggers first access
                    try {
                        context.getBean(BrokenLazyBeanConfig.BrokenService.class);
                        org.junit.jupiter.api.Assertions.fail("Expected BeanCreationException was not thrown on lazy bean access");
                    } catch (Exception ex) {
                        assertThat(ex.toString()).contains("Missing required HSM vault connection");
                    }
                });
    }

    @Configuration
    static class BrokenLazyBeanConfig {
        @Bean
        @org.springframework.context.annotation.Lazy
        public BrokenService brokenService() {
            return new BrokenService();
        }

        public static class BrokenService {
            public BrokenService() {
                // Simulates missing external secret / hardware security module connection
                throw new IllegalStateException("Missing required HSM vault connection during initialization");
            }
        }
    }
}
