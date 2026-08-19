package com.finflow.chapter130.incorrect;

import com.finflow.chapter130.domain.PaymentSettlementEntity;
import com.finflow.chapter130.domain.SettlementStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MergeReturnValueIgnoredServiceIncorrect {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void processSettlementIncorrectly(PaymentSettlementEntity detachedEntity) {
        // INCORRECT: Merge returns a managed instance, but the original reference remains detached.
        em.merge(detachedEntity);
        
        // This modification is done on the detached instance.
        // It will NOT be synchronized to the database upon transaction commit.
        detachedEntity.setStatus(SettlementStatus.SETTLED);
    }
}
