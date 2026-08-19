package com.finflow.chapter110.unit;

import com.finflow.chapter110.correct.aspect.PaymentAuditAspect;
import com.finflow.chapter110.domain.PaymentExecutionRequest;
import com.finflow.chapter110.incorrect.PaymentServiceSelfInvocationIncorrect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SelfInvocationFailureTest {

    @Autowired
    private PaymentServiceSelfInvocationIncorrect service;

    @Autowired
    private PaymentAuditAspect aspect;

    @BeforeEach
    void setUp() {
        aspect.clear();
    }

    @Test
    void testSelfInvocationBypassesProxy() {
        PaymentExecutionRequest request = new PaymentExecutionRequest(
                UUID.randomUUID().toString(),
                "CUST-001",
                10000L,
                "USD"
        );

        service.executePayment(request);

        // Assert that zero audit records were created because the proxy was bypassed!
        assertThat(aspect.getAuditRecords()).isEmpty();
    }
}
