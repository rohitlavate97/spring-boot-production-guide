package com.finflow.chapter130.unit;

import com.finflow.chapter130.domain.PaymentSettlementEntity;
import com.finflow.chapter130.domain.SettlementStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class EntityStateTransitionTest {

    @Autowired
    private EntityManager em;

    @Test
    void testTransientToManagedToDetached() {
        // Transient
        PaymentSettlementEntity entity = new PaymentSettlementEntity();
        entity.setId(UUID.randomUUID());
        entity.setAmountCents(1000L);
        entity.setStatus(SettlementStatus.PENDING);

        assertThat(em.contains(entity)).isFalse();

        // Managed via persist
        em.persist(entity);
        assertThat(em.contains(entity)).isTrue();

        em.flush(); // push to DB

        // Detached
        em.detach(entity);
        assertThat(em.contains(entity)).isFalse();
    }
}
