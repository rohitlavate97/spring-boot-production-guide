package com.finflow.chapter140.unit;

import com.finflow.chapter140.correct.PaymentLedgerRepository;
import com.finflow.chapter140.domain.PaymentLedgerEntity;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
public class ActionQueueUniqueConstraintTest {

    @Autowired
    private PaymentLedgerRepository repository;

    @Autowired
    private EntityManager entityManager;

    private UUID existingId;

    @BeforeEach
    public void setup() {
        existingId = UUID.randomUUID();
        PaymentLedgerEntity entity = new PaymentLedgerEntity(existingId, "TX-100", 5000L, "USD", "PENDING", Instant.now());
        repository.saveAndFlush(entity);
    }

    @Test
    public void testActionQueueOrder_throwsUniqueConstraintViolation() {
        UUID newId = UUID.randomUUID();

        // Hibernate's ActionQueue executes inserts before deletes!
        assertThatThrownBy(() -> {
            PaymentLedgerEntity existing = entityManager.find(PaymentLedgerEntity.class, existingId);
            entityManager.remove(existing);
            
            // Re-using the same unique entryCode "TX-100"
            PaymentLedgerEntity newEntity = new PaymentLedgerEntity(newId, "TX-100", 6000L, "USD", "COMPLETED", Instant.now());
            entityManager.persist(newEntity);
            
            entityManager.flush(); // Triggers the exception
        }).isInstanceOf(Exception.class)
          .hasMessageContaining("Unique index or primary key violation");
    }

    @Test
    public void testActionQueueOrder_withExplicitFlush_succeeds() {
        UUID newId = UUID.randomUUID();

        PaymentLedgerEntity existing = entityManager.find(PaymentLedgerEntity.class, existingId);
        entityManager.remove(existing);
        
        // Critical Fix: Flush to force the DELETE execution
        entityManager.flush();

        PaymentLedgerEntity newEntity = new PaymentLedgerEntity(newId, "TX-100", 6000L, "USD", "COMPLETED", Instant.now());
        entityManager.persist(newEntity);
        
        entityManager.flush(); // Succeeds

        PaymentLedgerEntity saved = repository.findById(newId).orElse(null);
        assertThat(saved).isNotNull();
        assertThat(saved.getEntryCode()).isEqualTo("TX-100");
    }
}
