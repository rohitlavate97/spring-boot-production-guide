package com.finflow.chapter130.unit;

import com.finflow.chapter130.domain.PaymentSettlementEntity;
import com.finflow.chapter130.domain.SettlementStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class BatchProcessingFlushClearTest {

    @Autowired
    private EntityManager em;

    @Test
    void testBatchProcessingFlushAndClear() {
        List<PaymentSettlementEntity> list = new ArrayList<>();
        for (int i = 0; i < 60; i++) {
            PaymentSettlementEntity entity = new PaymentSettlementEntity();
            entity.setId(UUID.randomUUID());
            entity.setStatus(SettlementStatus.PENDING);
            list.add(entity);
            em.persist(entity);
        }
        em.flush();
        em.clear();

        int batchSize = 50;
        for (int i = 0; i < list.size(); i++) {
            PaymentSettlementEntity detached = new PaymentSettlementEntity();
            detached.setId(list.get(i).getId());
            detached.setVersion(list.get(i).getVersion());
            detached.setStatus(SettlementStatus.PROCESSING);

            PaymentSettlementEntity managed = em.merge(detached);
            managed.setStatus(SettlementStatus.SETTLED);

            if (i > 0 && i % batchSize == 0) {
                em.flush();
                em.clear();
            }
        }
        
        // Flush remaining
        em.flush();
        em.clear();

        // Verify some
        PaymentSettlementEntity verified = em.find(PaymentSettlementEntity.class, list.get(0).getId());
        assertThat(verified.getStatus()).isEqualTo(SettlementStatus.SETTLED);
        
        PaymentSettlementEntity verifiedLast = em.find(PaymentSettlementEntity.class, list.get(59).getId());
        assertThat(verifiedLast.getStatus()).isEqualTo(SettlementStatus.SETTLED);
    }
}
