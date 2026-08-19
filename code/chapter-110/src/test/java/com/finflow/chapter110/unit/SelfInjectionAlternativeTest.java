package com.finflow.chapter110.unit;

import com.finflow.chapter110.correct.PaymentServiceSelfInjectionAlternative;
import com.finflow.chapter110.correct.aspect.PaymentAuditAspect;
import com.finflow.chapter110.domain.AuditRecord;
import com.finflow.chapter110.domain.PaymentExecutionRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SelfInjectionAlternativeTest {

    @Autowired
    private PaymentServiceSelfInjectionAlternative service;

    @Autowired
    private PaymentAuditAspect aspect;

    @BeforeEach
    void setUp() {
        aspect.clear();
    }

    @Test
    void testSelfInjectionCallTriggersProxy() {
        String paymentId = UUID.randomUUID().toString();
        PaymentExecutionRequest request = new PaymentExecutionRequest(
                paymentId,
                "CUST-003",
                5000L,
                "GBP"
        );

        service.executePayment(request);

        assertThat(aspect.getAuditRecords()).hasSize(1);
        AuditRecord record = aspect.getAuditRecords().peek();
        assertThat(record.paymentId()).isEqualTo(paymentId);
        assertThat(record.operation()).isEqualTo("SELF_INJECTED_AUDIT");
        assertThat(record.status()).isEqualTo("SUCCESS");
    }
}
