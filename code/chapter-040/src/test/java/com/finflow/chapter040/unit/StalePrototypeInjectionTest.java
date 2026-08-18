package com.finflow.chapter040.unit;

import com.finflow.chapter040.correct.PaymentProcessorWithProvider;
import com.finflow.chapter040.domain.PaymentRequest;
import com.finflow.chapter040.incorrect.GatewayConnectionIncorrect;
import com.finflow.chapter040.incorrect.PaymentProcessorIncorrect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StalePrototypeInjectionTest {

    @BeforeEach
    void resetCounters() {
        GatewayConnectionIncorrect.instanceCount.set(0);
        GatewayConnectionIncorrect.preDestroyCount.set(0);
    }

    @Test
    void injectingPrototypeIntoSingletonCreatesStaleState() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                PaymentProcessorIncorrect.class, GatewayConnectionIncorrect.class);

        PaymentProcessorIncorrect processor = context.getBean(PaymentProcessorIncorrect.class);

        GatewayConnectionIncorrect conn1 = processor.getConnection();
        GatewayConnectionIncorrect conn2 = processor.getConnection();

        // BUG PROVEN: Even though connection is a @Scope("prototype"), the singleton only got injected ONCE.
        assertEquals(conn1, conn2, "Both calls return the exact same instance!");

        context.close();
    }

    @Test
    void objectProviderReturnsFreshPrototypes() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(
                PaymentProcessorWithProvider.class, GatewayConnectionIncorrect.class);

        PaymentProcessorWithProvider processor = context.getBean(PaymentProcessorWithProvider.class);

        PaymentRequest req = new PaymentRequest(UUID.randomUUID(), "test-key", 1000L, "USD");
        
        // process() uses objectProvider.getObject() under the hood
        processor.process(req); 
        processor.process(req);

        // Since we reset the counter before the test suite, this test runs second 
        // within the class, but we need to account for the first test creating 1 instance.
        // Wait, @BeforeEach resets before EVERY test, so it should be exactly 2.
        assertEquals(2, GatewayConnectionIncorrect.instanceCount.get(), "Two fresh prototypes created");

        context.close();
    }
}
