package com.finflow.chapter200.repository;

import com.finflow.chapter200.domain.SettlementTransaction;
import com.finflow.chapter200.dto.SettlementSummaryDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementTransactionRepository extends JpaRepository<SettlementTransaction, UUID> {

    // 1. Fully SARGable & Aligned with Leftmost Prefix (merchant_id, status, created_at)
    List<SettlementTransaction> findByMerchantIdAndStatusAndCreatedAtBetweenOrderByCreatedAtDesc(
            String merchantId, String status, Instant start, Instant end);

    // 2. Leftmost Prefix Partial Match (merchant_id only)
    List<SettlementTransaction> findByMerchantId(String merchantId);

    // 3. Leftmost Prefix Match (merchant_id, status)
    List<SettlementTransaction> findByMerchantIdAndStatus(String merchantId, String status);

    // 4. Single-column index lookup
    Optional<SettlementTransaction> findByGatewayReference(String gatewayReference);

    // 5. Aggregation Query for Covering Index Optimization
    @Query("SELECT new com.finflow.chapter200.dto.SettlementSummaryDto(s.merchantId, s.status, SUM(s.amount), COUNT(s)) " +
           "FROM SettlementTransaction s " +
           "WHERE s.merchantId = :merchantId AND s.status = :status " +
           "GROUP BY s.merchantId, s.status")
    Optional<SettlementSummaryDto> summarizeMerchantSettlements(
            @Param("merchantId") String merchantId,
            @Param("status") String status);

    // 6. Non-SARGable Query (Wraps column in function - anti-pattern demonstration)
    @Query("SELECT s FROM SettlementTransaction s WHERE LOWER(s.merchantId) = LOWER(:merchantId)")
    List<SettlementTransaction> findByMerchantIdLowerWrapped(@Param("merchantId") String merchantId);

    // 7. Violates Leftmost Prefix (Queries status and created_at without merchant_id)
    List<SettlementTransaction> findByStatusAndCreatedAtBetween(String status, Instant start, Instant end);
}
