package com.finflow.chapter200.incorrect;

import com.finflow.chapter200.domain.SettlementTransaction;
import com.finflow.chapter200.repository.SettlementTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Non-SARGable queries: Wraps indexed columns in functions (LOWER, DATE), invalidating B-Tree indexes.
 * 2. Leftmost Prefix violation: Queries on (status, created_at) when index is (merchant_id, status, created_at).
 * 3. Full entity loading for aggregate computations in application memory instead of database index aggregation.
 */
@Service
@Transactional(readOnly = true)
public class SettlementQueryServiceIncorrect {

    private final SettlementTransactionRepository repository;

    public SettlementQueryServiceIncorrect(SettlementTransactionRepository repository) {
        this.repository = repository;
    }

    /**
     * Anti-Pattern 1: Function Call on Indexed Column (Non-SARGable).
     * Prevents PostgreSQL from performing Index Scan, forcing a full Seq Scan!
     */
    public List<SettlementTransaction> searchMerchantCaseInsensitive(String merchantId) {
        return repository.findByMerchantIdLowerWrapped(merchantId);
    }

    /**
     * Anti-Pattern 2: Violating Leftmost Prefix Rule.
     * Table has composite index (merchant_id, status, created_at).
     * Querying only by status and created_at skips merchant_id, forcing Seq Scan!
     */
    public List<SettlementTransaction> findByStatusAndRange(String status, Instant from, Instant to) {
        return repository.findByStatusAndCreatedAtBetween(status, from, to);
    }
}
