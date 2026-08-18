package com.finflow.chapter040.unit;

import com.finflow.chapter040.incorrect.GatewayConnectionIncorrect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PrototypeLifecycleTest {

    @BeforeEach
    void resetCounters() {
        GatewayConnectionIncorrect.instanceCount.set(0);
        GatewayConnectionIncorrect.preDestroyCount.set(0);
    }

    @Test
    void prototypePreDestroyIsNeverCalled() {
        // Arrange
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(GatewayConnectionIncorrect.class);

        // Act - Request 3 prototypes
        context.getBean(GatewayConnectionIncorrect.class);
        context.getBean(GatewayConnectionIncorrect.class);
        context.getBean(GatewayConnectionIncorrect.class);

        // Close the context - triggers lifecycle teardown
        context.close();

        // Assert
        assertEquals(3, GatewayConnectionIncorrect.instanceCount.get(), "Three instances should be created");
        assertEquals(0, GatewayConnectionIncorrect.preDestroyCount.get(), "@PreDestroy is NEVER called for prototype beans");
    }
}
