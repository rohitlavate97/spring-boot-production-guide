package com.finflow.chapter160.correct;

import com.finflow.chapter160.domain.SequenceSettlementRecord;
import com.finflow.chapter160.domain.SettlementRecord;
import com.finflow.chapter160.domain.SettlementStatus;
import com.finflow.chapter160.dto.SettlementIngestItem;
import com.finflow.chapter160.repository.SettlementRecordRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * CORRECT IMPLEMENTATION:
 * 1. Employs chunked batching with periodic em.flush() and em.clear() every BATCH_SIZE items.
 * 2. Bounds First-Level Cache (Persistence Context) memory consumption to O(BATCH_SIZE).
 * 3. Uses UUID or Sequence identifiers (allocationSize = 50) allowing Hibernate to batch JDBC INSERTs.
 * 4. Uses JPQL bulk UPDATE to modify thousands of records in a single database statement.
 */
@Service
public class ChunkedHibernateBatchService {

    private static final int BATCH_SIZE = 50;

    @PersistenceContext
    private EntityManager entityManager;

    private final SettlementRecordRepository settlementRecordRepository;

    public ChunkedHibernateBatchService(SettlementRecordRepository settlementRecordRepository) {
        this.settlementRecordRepository = settlementRecordRepository;
    }

    /**
     * Ingests records using Chunked Hibernate Batching (UUID assigned IDs).
     */
    @Transactional
    public void ingestWithChunkedFlushClear(String batchId, List<SettlementIngestItem> items) {
        for (int i = 0; i < items.size(); i++) {
            SettlementIngestItem item = items.get(i);
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

            entityManager.persist(record);

            // Flush changes to DB and clear L1 cache every 50 records to prevent memory exhaustion
            if ((i + 1) % BATCH_SIZE == 0 || (i + 1) == items.size()) {
                entityManager.flush();
                entityManager.clear();
            }
        }
    }

    /**
     * Ingests records using SequenceGenerator with allocationSize = 50.
     */
    @Transactional
    public void ingestWithSequenceBatching(String batchId, List<SettlementIngestItem> items) {
        for (int i = 0; i < items.size(); i++) {
            SettlementIngestItem item = items.get(i);
            SequenceSettlementRecord record = new SequenceSettlementRecord(
                    batchId,
                    item.merchantCode(),
                    item.grossAmount(),
                    SettlementStatus.PENDING,
                    Instant.now()
            );

            entityManager.persist(record);

            if ((i + 1) % BATCH_SIZE == 0 || (i + 1) == items.size()) {
                entityManager.flush();
                entityManager.clear();
            }
        }
    }

    /**
     * Bulk status update executing exactly 1 SQL UPDATE statement.
     */
    @Transactional
    public int bulkUpdateStatus(String batchId, SettlementStatus oldStatus, SettlementStatus newStatus) {
        return settlementRecordRepository.bulkUpdateStatusByBatchId(batchId, oldStatus, newStatus);
    }
}
