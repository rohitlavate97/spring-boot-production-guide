package com.finflow.chapter160.incorrect;

import com.finflow.chapter160.domain.IdentitySettlementRecord;
import com.finflow.chapter160.domain.SettlementRecord;
import com.finflow.chapter160.domain.SettlementStatus;
import com.finflow.chapter160.dto.SettlementIngestItem;
import com.finflow.chapter160.repository.IdentitySettlementRepository;
import com.finflow.chapter160.repository.SettlementRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Calls repository.saveAll() with unbounded lists, loading 50,000+ entities into the
 *    Hibernate Persistence Context (L1 Cache) simultaneously without chunked flush/clear.
 * 2. Uses GenerationType.IDENTITY, which disables Hibernate JDBC batching completely because
 *    Hibernate must immediately execute each INSERT to retrieve generated primary keys.
 * 3. Updates batch records by loading all entities into memory and modifying fields one by one,
 *    triggering N separate SQL UPDATE statements instead of a single JPQL bulk UPDATE.
 */
@Service
public class NaiveBulkSaveServiceIncorrect {

    private final SettlementRecordRepository settlementRecordRepository;
    private final IdentitySettlementRepository identitySettlementRepository;

    public NaiveBulkSaveServiceIncorrect(SettlementRecordRepository settlementRecordRepository,
                                         IdentitySettlementRepository identitySettlementRepository) {
        this.settlementRecordRepository = settlementRecordRepository;
        this.identitySettlementRepository = identitySettlementRepository;
    }

    /**
     * Anti-Pattern 1: Unchunked saveAll() causing L1 cache memory bloat.
     */
    @Transactional
    public void ingestBulkNaive(String batchId, List<SettlementIngestItem> items) {
        List<SettlementRecord> records = new ArrayList<>(items.size());
        for (SettlementIngestItem item : items) {
            records.add(new SettlementRecord(
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
            ));
        }

        // All entities remain managed in the Persistence Context until transaction commit
        settlementRecordRepository.saveAll(records);
    }

    /**
     * Anti-Pattern 2: GenerationType.IDENTITY disables JDBC batching.
     * Executes 1 round-trip INSERT per entity synchronously!
     */
    @Transactional
    public void ingestWithIdentityDisablingBatching(String batchId, List<SettlementIngestItem> items) {
        List<IdentitySettlementRecord> records = new ArrayList<>(items.size());
        for (SettlementIngestItem item : items) {
            records.add(new IdentitySettlementRecord(
                    batchId,
                    item.merchantCode(),
                    item.grossAmount(),
                    SettlementStatus.PENDING,
                    Instant.now()
            ));
        }

        identitySettlementRepository.saveAll(records);
    }

    /**
     * Anti-Pattern 3: In-memory bulk update instead of JPQL/SQL bulk update.
     * Executes 1 SELECT + N individual UPDATE statements.
     */
    @Transactional
    public void naiveStatusUpdate(String batchId, SettlementStatus newStatus) {
        List<SettlementRecord> records = settlementRecordRepository.findAllByBatchId(batchId);
        for (SettlementRecord record : records) {
            record.setStatus(newStatus);
            // Hibernate dirty checking fires individual UPDATE for every row at flush time
        }
    }
}
