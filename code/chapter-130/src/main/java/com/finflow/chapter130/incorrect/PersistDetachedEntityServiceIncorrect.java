package com.finflow.chapter130.incorrect;

import com.finflow.chapter130.domain.PaymentSettlementEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersistDetachedEntityServiceIncorrect {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public void saveDetachedEntityIncorrectly(PaymentSettlementEntity detachedEntity) {
        // INCORRECT: persist() is meant for new, transient entities without an ID.
        // Since detachedEntity already has an ID assigned (e.g., from DB or manually if not generated),
        // this will throw PersistentObjectException.
        em.persist(detachedEntity);
    }
}
