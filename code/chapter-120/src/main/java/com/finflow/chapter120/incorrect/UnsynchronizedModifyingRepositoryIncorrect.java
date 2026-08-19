package com.finflow.chapter120.incorrect;

import com.finflow.chapter120.domain.PaymentIntentEntity;
import com.finflow.chapter120.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface UnsynchronizedModifyingRepositoryIncorrect extends JpaRepository<PaymentIntentEntity, UUID> {

    // Incorrect: Missing clearAutomatically = true. If an entity is already in the L1 cache,
    // subsequent findById within the same transaction will return the stale state from cache.
    @Modifying
    @Query("UPDATE PaymentIntentEntity p SET p.status = :newStatus WHERE p.id = :id")
    int updateStatusWithoutClearingCache(@Param("id") UUID id, @Param("newStatus") PaymentStatus newStatus);
}
