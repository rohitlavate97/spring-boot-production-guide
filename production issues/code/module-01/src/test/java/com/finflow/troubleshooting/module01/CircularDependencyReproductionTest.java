package com.finflow.troubleshooting.module01;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Service;

import static org.assertj.core.api.Assertions.assertThat;

public class CircularDependencyReproductionTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner();

    @Test
    void testCircularConstructorInjectionFailsOnStartup() {
        contextRunner
                .withUserConfiguration(CircularConfig.class)
                .withPropertyValues("spring.main.allow-circular-references=false")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasRootCauseInstanceOf(BeanCurrentlyInCreationException.class)
                            .hasMessageContaining("circular reference");
                });
    }

    @Configuration
    static class CircularConfig {
        @Service
        static class CircularOrderService {
            private final CircularPaymentService paymentService;
            public CircularOrderService(CircularPaymentService paymentService) {
                this.paymentService = paymentService;
            }
        }

        @Service
        static class CircularPaymentService {
            private final CircularOrderService orderService;
            public CircularPaymentService(CircularOrderService orderService) {
                this.orderService = orderService;
            }
        }
    }
}
