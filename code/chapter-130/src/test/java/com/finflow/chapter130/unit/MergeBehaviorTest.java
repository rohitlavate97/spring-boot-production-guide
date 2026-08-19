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
public class MergeBehaviorTest {

    @Autowired
    private EntityManager em;

    @Test
    void testMergeReturnValueIgnored() {
        PaymentSettlementEntity entity = new PaymentSettlementEntity();
        UUID id = UUID.randomUUID();
        entity.setId(id);
        entity.setStatus(SettlementStatus.PENDING);

        em.persist(entity);
        em.flush();
        em.clear();

        // Detached entity
        PaymentSettlementEntity detached = new PaymentSettlementEntity();
        detached.setId(id);
        detached.setVersion(entity.getVersion());
        detached.setStatus(SettlementStatus.PROCESSING);

        // Incorrect merge usage
        em.merge(detached);
        detached.setStatus(SettlementStatus.SETTLED); // Modified detached reference

        em.flush();
        em.clear();

        // Check from DB
        PaymentSettlementEntity fromDb = em.find(PaymentSettlementEntity.class, id);
        // Status remains PROCESSING because we modified the detached reference
        assertThat(fromDb.getStatus()).isEqualTo(SettlementStatus.PROCESSING);
    }

    @Test
    void testMergeCorrectly() {
        PaymentSettlementEntity entity = new PaymentSettlementEntity();
        UUID id = UUID.randomUUID();
        entity.setId(id);
        entity.setStatus(SettlementStatus.PENDING);

        em.persist(entity);
        em.flush();
        em.clear();

        PaymentSettlementEntity detached = new PaymentSettlementEntity();
        detached.setId(id);
        detached.setVersion(entity.getVersion());
        detached.setStatus(SettlementStatus.PROCESSING);

        // Correct merge usage
        PaymentSettlementEntity managed = em.merge(detached);
        managed.setStatus(SettlementStatus.SETTLED); // Modified managed reference

        em.flush();
        em.clear();

        PaymentSettlementEntity fromDb = em.find(PaymentSettlementEntity.class, id);
        // Status is SETTLED
        assertThat(fromDb.getStatus()).isEqualTo(SettlementStatus.SETTLED);
    }
}
