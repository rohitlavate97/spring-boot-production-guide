package com.finflow.chapter140.correct;

import com.finflow.chapter140.domain.MerchantConfigEntity;
import com.finflow.chapter140.domain.PaymentLedgerEntity;
import jakarta.persistence.EntityManager;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PersistenceContextOptimizationService {

    private final MerchantConfigRepository merchantConfigRepository;
    private final PaymentLedgerRepository paymentLedgerRepository;
    private final EntityManager entityManager;

    public PersistenceContextOptimizationService(MerchantConfigRepository merchantConfigRepository,
                                                 PaymentLedgerRepository paymentLedgerRepository,
                                                 EntityManager entityManager) {
        this.merchantConfigRepository = merchantConfigRepository;
        this.paymentLedgerRepository = paymentLedgerRepository;
        this.entityManager = entityManager;
    }

    // Best Practice: readOnly = true prevents dirty checking updates and avoids creating snapshots.
    @Transactional(readOnly = true)
    public String getFormattedMerchantConfigSafe(UUID configId) {
        MerchantConfigEntity config = merchantConfigRepository.findById(configId)
                .orElseThrow(() -> new IllegalArgumentException("Config not found"));
        
        String originalValue = config.getConfigValue();
        String formattedValue = originalValue != null ? originalValue.trim().toUpperCase() : "";
        // Even if we mutate the object here, Hibernate won't issue an UPDATE because of readOnly=true
        config.setConfigValue(formattedValue);
        
        return formattedValue;
    }

    // Best Practice: Manually flushing to enforce execution order within ActionQueue
    @Transactional
    public void swapLedgerEntryCodeSafe(UUID existingId, UUID newId, String entryCode) {
        PaymentLedgerEntity existing = entityManager.find(PaymentLedgerEntity.class, existingId);
        
        if (existing != null) {
            entityManager.remove(existing);
        }
        
        // Critical: Flush immediately to execute the DELETE statement before the INSERT statement
        entityManager.flush();
        
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
    
    // Best Practice: Preventing premature flushes in query-heavy loops
    @Transactional
    public void processLargeBatchWithoutPrematureFlushes(List<UUID> ledgerIds) {
        Session session = entityManager.unwrap(Session.class);
        // Setting FlushMode to COMMIT prevents automatic flushing before query executions
        session.setHibernateFlushMode(FlushMode.COMMIT);
        
        for (UUID id : ledgerIds) {
            PaymentLedgerEntity entity = paymentLedgerRepository.findById(id).orElse(null);
            if (entity != null) {
                entity.setStatus("COMPLETED");
                // Suppose we do a JPQL query here to check something else.
                // With FlushMode.AUTO, this query would trigger a flush of the above mutation!
                // With FlushMode.COMMIT, it delays the flush until transaction ends.
                List<PaymentLedgerEntity> others = paymentLedgerRepository.findAll();
            }
        }
    }
}
