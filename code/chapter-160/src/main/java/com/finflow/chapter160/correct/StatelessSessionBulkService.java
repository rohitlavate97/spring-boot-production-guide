package com.finflow.chapter160.correct;

import com.finflow.chapter160.domain.SettlementRecord;
import com.finflow.chapter160.domain.SettlementStatus;
import com.finflow.chapter160.dto.SettlementIngestItem;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.StatelessSession;
import org.hibernate.Transaction;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * STATELESS SESSION BATCH SERVICE:
 * Uses Hibernate StatelessSession for high-performance batch insertion.
 * A StatelessSession does not maintain a first-level cache, does not perform dirty checking,
 * and does not hold entity snapshots, making it immune to heap exhaustion.
 */
@Service
public class StatelessSessionBulkService {

    private final SessionFactory sessionFactory;

    public StatelessSessionBulkService(EntityManagerFactory entityManagerFactory) {
        this.sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
    }

    public void ingestViaStatelessSession(String batchId, List<SettlementIngestItem> items) {
        try (StatelessSession statelessSession = sessionFactory.openStatelessSession()) {
            Transaction tx = statelessSession.beginTransaction();
            try {
                for (SettlementIngestItem item : items) {
                    SettlementRecord record = new SettlementRecord(
                            UUID.randomUUID(),
                            batchId,
                            item.merchantCode(),
                            item.transactionRef(),
                            item.grossAmount(),
                            item.feeAmount(),
                            item.netAmount(),
                            item.currency(),
                            SettlementStatus.PENDING,
                            Instant.now()
                    );
                    statelessSession.insert(record);
                }
                tx.commit();
            } catch (Exception ex) {
                if (tx.isActive()) {
                    tx.rollback();
                }
                throw ex;
            }
        }
    }
}
