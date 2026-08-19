package com.finflow.chapter130.correct;

import com.finflow.chapter130.domain.PaymentSettlementEntity;
import com.finflow.chapter130.domain.SettlementStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SettlementLifecycleService {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public PaymentSettlementEntity processSettlementCorrectly(PaymentSettlementEntity detachedEntity) {
        // CORRECT: Reassign the returned managed reference
        PaymentSettlementEntity managed = em.merge(detachedEntity);
        
        // Modifying the managed instance ensures changes are flushed
        managed.setStatus(SettlementStatus.SETTLED);
        
        return managed;
    }

    @Transactional
    public void createNewSettlement(PaymentSettlementEntity transientEntity) {
        // CORRECT: persist is used for truly transient (new) entities
        em.persist(transientEntity);
    }

    @Transactional(readOnly = true)
    public PaymentSettlementEntity readOnlySettlement(UUID id) {
        // CORRECT: readOnly = true tells Hibernate to disable dirty checking optimizations where possible
        PaymentSettlementEntity entity = em.find(PaymentSettlementEntity.class, id);
        // If we modify it here, depending on Hibernate version and optimizations, it might not be flushed
        // but it's best practice not to modify it anyway.
        return entity;
    }

    @Transactional
    public void processBatch(List<PaymentSettlementEntity> detachedEntities) {
        int batchSize = 50;
        for (int i = 0; i < detachedEntities.size(); i++) {
            PaymentSettlementEntity detached = detachedEntities.get(i);
            PaymentSettlementEntity managed = em.merge(detached);
            managed.setStatus(SettlementStatus.SETTLED);

            if (i > 0 && i % batchSize == 0) {
                // CORRECT: Chunked processing to avoid memory exhaustion
                em.flush();
                em.clear();
            }
        }
    }

    public boolean isManaged(PaymentSettlementEntity entity) {
        return em.contains(entity);
    }
}
