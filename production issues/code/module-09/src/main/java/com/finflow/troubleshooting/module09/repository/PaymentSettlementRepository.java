package com.finflow.troubleshooting.module09.repository;

import com.finflow.troubleshooting.module09.entity.SettlementEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentSettlementRepository extends JpaRepository<SettlementEntity, Long> {
    Optional<SettlementEntity> findByTransactionId(String transactionId);
}
