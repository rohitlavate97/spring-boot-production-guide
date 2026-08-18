package com.finflow.chapter020.repository;

import com.finflow.chapter020.domain.PaymentIntent;
import com.finflow.chapter020.domain.ReportSummary;
import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public interface PaymentIntentRepository extends JpaRepository<PaymentIntent, UUID> {
    
    // Used by incorrect implementation - loads everything into memory
    List<PaymentIntent> findByMerchantIdAndCreatedAtBetween(
        UUID merchantId, LocalDateTime start, LocalDateTime end);
    
    // Used by correct implementation - streaming
    @QueryHints(@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE, value = "500"))
    @Query("SELECT p FROM PaymentIntent p WHERE p.merchantId = :merchantId AND p.createdAt BETWEEN :start AND :end")
    Stream<PaymentIntent> streamByMerchantIdAndCreatedAtBetween(
        @Param("merchantId") UUID merchantId,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
    
    // Used by correct implementation - database-side aggregation
    @Query("""
        SELECT new com.finflow.chapter020.domain.ReportSummary(
            p.merchantId, :month,
            COUNT(p), SUM(p.amountCents),
            SUM(CASE WHEN p.status = 'COMPLETED' THEN 1 ELSE 0 END),
            SUM(CASE WHEN p.status = 'FAILED' THEN 1 ELSE 0 END),
            null
        )
        FROM PaymentIntent p
        WHERE p.merchantId = :merchantId AND p.createdAt BETWEEN :start AND :end
        """)
    ReportSummary aggregateReport(
        @Param("merchantId") UUID merchantId,
        @Param("month") YearMonth month,
        @Param("start") LocalDateTime start,
        @Param("end") LocalDateTime end);
}
