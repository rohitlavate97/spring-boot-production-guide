package com.finflow.chapter120.unit;

import com.finflow.chapter120.correct.repository.PaymentIntentRepository;
import com.finflow.chapter120.correct.specification.PaymentIntentSpecifications;
import com.finflow.chapter120.correct.specification.PaymentSearchCriteria;
import com.finflow.chapter120.domain.PaymentIntentEntity;
import com.finflow.chapter120.domain.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class PaymentSpecificationTest {

    @Autowired
    private PaymentIntentRepository repository;

    private UUID customer1;
    private UUID customer2;

    @BeforeEach
    void setUp() {
        customer1 = UUID.randomUUID();
        customer2 = UUID.randomUUID();

        repository.save(new PaymentIntentEntity(UUID.randomUUID(), customer1, 500L, "USD", PaymentStatus.CREATED, "k1"));
        repository.save(new PaymentIntentEntity(UUID.randomUUID(), customer1, 1500L, "EUR", PaymentStatus.CAPTURED, "k2"));
        repository.save(new PaymentIntentEntity(UUID.randomUUID(), customer2, 2500L, "USD", PaymentStatus.FAILED, "k3"));
    }

    @Test
    void testSpecificationByCustomerAndStatus() {
        PaymentSearchCriteria criteria = new PaymentSearchCriteria(
                customer1, PaymentStatus.CAPTURED, null, null, null, null, null);

        List<PaymentIntentEntity> results = repository.findAll(PaymentIntentSpecifications.withCriteria(criteria));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getCurrency()).isEqualTo("EUR");
    }

    @Test
    void testSpecificationByAmountRange() {
        PaymentSearchCriteria criteria = new PaymentSearchCriteria(
                null, null, "USD", 0L, 2000L, null, null);

        List<PaymentIntentEntity> results = repository.findAll(PaymentIntentSpecifications.withCriteria(criteria));

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().getAmountCents()).isEqualTo(500L);
    }
}
