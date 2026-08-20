package com.finflow.chapter200.correct;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.chapter200.domain.SettlementTransaction;
import com.finflow.chapter200.dto.SettlementSummaryDto;
import com.finflow.chapter200.repository.SettlementTransactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class SettlementQueryServiceCorrect {

    private final SettlementTransactionRepository repository;
    private final ObjectMapper objectMapper;

    public SettlementQueryServiceCorrect(SettlementTransactionRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    /**
     * Correct Pattern 1: Exact Leftmost Prefix Alignment.
     * Uses (merchant_id, status, created_at) composite index for lightning-fast B-Tree lookup.
     */
    public List<SettlementTransaction> findMerchantSettlements(String merchantId, String status, Instant start, Instant end) {
        return repository.findByMerchantIdAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(merchantId, status, start, end);
    }

    /**
     * Correct Pattern 2: Leftmost Prefix Subset (merchant_id and status).
     * Uses leading columns of composite index effectively.
     */
    public List<SettlementTransaction> findByMerchantAndStatus(String merchantId, String status) {
        return repository.findByMerchantIdAndStatus(merchantId, status);
    }

    /**
     * Correct Pattern 3: Database-Level Aggregation for Covering Index Scan.
     * Computes SUM and COUNT directly in database engine avoiding heap entity hydration.
     */
    public Optional<SettlementSummaryDto> getSettlementSummary(String merchantId, String status) {
        return repository.summarizeMerchantSettlements(merchantId, status);
    }

    /**
     * Correct Pattern 4: Structured JSON attribute parsing.
     */
    public Optional<String> extractRoutingCodeFromMetadata(SettlementTransaction tx) {
        if (tx.getMetadataJson() == null || tx.getMetadataJson().isBlank()) {
            return Optional.empty();
        }
        try {
            JsonNode root = objectMapper.readTree(tx.getMetadataJson());
            if (root.has("routing_code")) {
                return Optional.of(root.get("routing_code").asText());
            }
        } catch (Exception e) {
            // Log parse failure in real system
        }
        return Optional.empty();
    }
}
