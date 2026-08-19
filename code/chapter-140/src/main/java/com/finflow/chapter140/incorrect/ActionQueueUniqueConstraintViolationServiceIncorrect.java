package com.finflow.chapter140.incorrect;

import com.finflow.chapter140.domain.PaymentLedgerEntity;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.UUID;

@Service
public class ActionQueueUniqueConstraintViolationServiceIncorrect {

    private final EntityManager entityManager;

    public ActionQueueUniqueConstraintViolationServiceIncorrect(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Transactional
    public void swapLedgerEntryCode(UUID existingId, UUID newId, String entryCode) {
        PaymentLedgerEntity existing = entityManager.find(PaymentLedgerEntity.class, existingId);
        
        if (existing != null) {
            entityManager.remove(existing);
        }
        
        // We removed the existing entity. Now we want to reuse the entryCode which has a unique constraint.
        // Because of Hibernate's ActionQueue, the SQL INSERT executes BEFORE the SQL DELETE by default.
        // This results in a UniqueConstraintViolation unless we flush between remove and persist.
        PaymentLedgerEntity newEntity = new PaymentLedgerEntity(
                newId, 
                entryCode, 
                1000L, 
                "USD", 
                "PROCESSED", 
                Instant.now()
        );
        
        entityManager.persist(newEntity);
    }
}
