package com.finflow.chapter120.unit;

import com.finflow.chapter120.correct.service.PaymentQueryService;
import com.finflow.chapter120.domain.PaymentIntentEntity;
import com.finflow.chapter120.domain.PaymentIntentSummary;
import com.finflow.chapter120.domain.PaymentStatus;
import com.finflow.chapter120.correct.repository.PaymentIntentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class PaymentQueryServiceTest {

    @Autowired
    private PaymentQueryService paymentQueryService;

    @Autowired
    private PaymentIntentRepository repository;

    private UUID customerId;
    private UUID paymentId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        paymentId = UUID.randomUUID();
        PaymentIntentEntity entity = new PaymentIntentEntity(paymentId, customerId, 5000L, "USD", PaymentStatus.CREATED, "idemp-1");
        repository.saveAndFlush(entity);
    }

    @Test
    void testGetSummaries() {
        List<PaymentIntentSummary> summaries = paymentQueryService.getSummariesForCustomer(customerId);
        assertThat(summaries).hasSize(1);
        assertThat(summaries.getFirst().amountCents()).isEqualTo(5000L);
    }

    @Test
    void testUpdatePaymentStatus() {
        boolean success = paymentQueryService.updatePaymentStatus(paymentId, PaymentStatus.CREATED, PaymentStatus.AUTHORIZED);
        assertThat(success).isTrue();

        PaymentIntentEntity updated = repository.findById(paymentId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.AUTHORIZED);
    }
}
