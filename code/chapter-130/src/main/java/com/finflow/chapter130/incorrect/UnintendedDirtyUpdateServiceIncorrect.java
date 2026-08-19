package com.finflow.chapter130.incorrect;

import com.finflow.chapter130.domain.PaymentSettlementEntity;
import com.finflow.chapter130.domain.SettlementStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UnintendedDirtyUpdateServiceIncorrect {

    @PersistenceContext
    private EntityManager em;

    @Transactional
    public PaymentSettlementEntity readAndAccidentallyModify(UUID id) {
        PaymentSettlementEntity entity = em.find(PaymentSettlementEntity.class, id);
        
        // INCORRECT: Modifying a managed entity in a transaction (even if meant for read)
        // triggers dirty checking. This will execute an UPDATE statement at commit time.
        entity.setStatus(SettlementStatus.PROCESSING);
        
        return entity;
    }
}
