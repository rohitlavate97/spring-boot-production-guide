package com.finflow.chapter140.unit;

import com.finflow.chapter140.domain.PaymentLedgerEntity;
import jakarta.persistence.EntityManager;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class FlushModeCommitTest {

    @Autowired
    private EntityManager entityManager;

    private UUID id1;
    private UUID id2;

    @BeforeEach
    public void setup() {
        id1 = UUID.randomUUID();
        id2 = UUID.randomUUID();
        entityManager.persist(new PaymentLedgerEntity(id1, "TX-A", 100L, "USD", "PENDING", Instant.now()));
        entityManager.persist(new PaymentLedgerEntity(id2, "TX-B", 200L, "USD", "PENDING", Instant.now()));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    public void testFlushModeAuto_triggersPrematureFlush() {
        Session session = entityManager.unwrap(Session.class);
        Statistics stats = session.getSessionFactory().getStatistics();
        stats.clear();

        PaymentLedgerEntity entity = entityManager.find(PaymentLedgerEntity.class, id1);
        entity.setStatus("COMPLETED");

        // Executing a JPQL query triggers an auto-flush because the query might depend on the pending update
        entityManager.createQuery("SELECT p FROM PaymentLedgerEntity p", PaymentLedgerEntity.class).getResultList();

        assertThat(stats.getFlushCount()).isGreaterThan(0);
    }

    @Test
    public void testFlushModeCommit_preventsPrematureFlush() {
        Session session = entityManager.unwrap(Session.class);
        Statistics stats = session.getSessionFactory().getStatistics();
        stats.clear();

        session.setHibernateFlushMode(FlushMode.COMMIT);

        PaymentLedgerEntity entity = entityManager.find(PaymentLedgerEntity.class, id1);
        entity.setStatus("COMPLETED");

        // Executing a JPQL query DOES NOT trigger an auto-flush because we set FlushMode.COMMIT
        entityManager.createQuery("SELECT p FROM PaymentLedgerEntity p", PaymentLedgerEntity.class).getResultList();

        assertThat(stats.getFlushCount()).isEqualTo(0);
        
        session.flush(); // Manual flush or commit will trigger it
        assertThat(stats.getFlushCount()).isEqualTo(1);
    }
}
