package com.finflow.chapter120.unit;

import com.finflow.chapter120.correct.repository.PaymentIntentRepository;
import com.finflow.chapter120.domain.PaymentIntentEntity;
import com.finflow.chapter120.domain.PaymentIntentSummary;
import com.finflow.chapter120.domain.PaymentIntentView;
import com.finflow.chapter120.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PaymentIntentRepositoryTest {

    @Autowired
    private PaymentIntentRepository repository;

    private UUID customerId;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        PaymentIntentEntity p1 = new PaymentIntentEntity(UUID.randomUUID(), customerId, 1000L, "USD", PaymentStatus.CREATED, "key1");
        PaymentIntentEntity p2 = new PaymentIntentEntity(UUID.randomUUID(), customerId, 2000L, "USD", PaymentStatus.AUTHORIZED, "key2");
        repository.saveAll(List.of(p1, p2));
    }

    @Test
    void testDerivedQuery() {
        List<PaymentIntentEntity> results = repository.findByCustomerIdAndStatus(customerId, PaymentStatus.CREATED);
        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getStatus()).isEqualTo(PaymentStatus.CREATED);
    }

    @Test
    void testDtoProjection() {
        List<PaymentIntentSummary> summaries = repository.findSummariesByCustomerId(customerId);
        assertThat(summaries).hasSize(2);
        assertThat(summaries).extracting(PaymentIntentSummary::amountCents)
                .containsExactlyInAnyOrder(1000L, 2000L);
    }

    @Test
    void testInterfaceProjection() {
        List<PaymentIntentView> views = repository.findViewByCustomerId(customerId);
        assertThat(views).hasSize(2);
        assertThat(views).extracting(PaymentIntentView::getCustomerId)
                .containsOnly(customerId);
    }
}
