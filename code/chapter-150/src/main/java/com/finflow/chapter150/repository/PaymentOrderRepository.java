package com.finflow.chapter150.repository;

import com.finflow.chapter150.domain.PaymentOrder;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentOrderRepository extends JpaRepository<PaymentOrder, UUID> {

    // 1. Unoptimized query (triggers N+1 when navigating items or merchantAccount)
    List<PaymentOrder> findAllByMerchantId(String merchantId);

    // 2. JOIN FETCH query (resolves N+1 in a single SQL query)
    @Query("SELECT DISTINCT p FROM PaymentOrder p " +
           "JOIN FETCH p.merchantAccount " +
           "LEFT JOIN FETCH p.items " +
           "WHERE p.merchantId = :merchantId")
    List<PaymentOrder> findAllWithItemsAndMerchantByMerchantId(@Param("merchantId") String merchantId);

    // 3. EntityGraph query (dynamically specifies fetch plan via JPA 2.1 EntityGraph)
    @EntityGraph(attributePaths = {"merchantAccount", "items"}, type = EntityGraph.EntityGraphType.FETCH)
    @Query("SELECT p FROM PaymentOrder p WHERE p.merchantId = :merchantId")
    List<PaymentOrder> findAllWithEntityGraphByMerchantId(@Param("merchantId") String merchantId);

    // 4. Single entity fetch with items and audit logs
    @Query("SELECT p FROM PaymentOrder p " +
           "JOIN FETCH p.merchantAccount " +
           "LEFT JOIN FETCH p.items " +
           "WHERE p.id = :id")
    Optional<PaymentOrder> findByIdWithDetails(@Param("id") UUID id);
}
