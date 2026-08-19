package com.finflow.chapter120.correct.repository;

import com.finflow.chapter120.domain.PaymentIntentEntity;
import com.finflow.chapter120.domain.PaymentIntentSummary;
import com.finflow.chapter120.domain.PaymentIntentView;
import com.finflow.chapter120.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface PaymentIntentRepository extends JpaRepository<PaymentIntentEntity, UUID>, JpaSpecificationExecutor<PaymentIntentEntity> {

    List<PaymentIntentEntity> findByCustomerIdAndStatus(UUID customerId, PaymentStatus status);

    @Query("SELECT new com.finflow.chapter120.domain.PaymentIntentSummary(p.id, p.customerId, p.amountCents, p.currency, p.status, p.createdAt) FROM PaymentIntentEntity p WHERE p.customerId = :customerId")
    List<PaymentIntentSummary> findSummariesByCustomerId(@Param("customerId") UUID customerId);

    List<PaymentIntentView> findViewByCustomerId(UUID customerId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE PaymentIntentEntity p SET p.status = :newStatus, p.updatedAt = :updatedAt WHERE p.id = :id AND p.status = :currentStatus")
    int updateStatus(@Param("id") UUID id, @Param("currentStatus") PaymentStatus currentStatus, @Param("newStatus") PaymentStatus newStatus, @Param("updatedAt") java.time.Instant updatedAt);

    default int updateStatus(UUID id, PaymentStatus currentStatus, PaymentStatus newStatus) {
        return updateStatus(id, currentStatus, newStatus, java.time.Instant.now());
    }
}
