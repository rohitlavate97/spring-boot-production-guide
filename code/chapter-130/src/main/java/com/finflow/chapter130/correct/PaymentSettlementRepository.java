package com.finflow.chapter130.correct;

import com.finflow.chapter130.domain.PaymentSettlementEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PaymentSettlementRepository extends JpaRepository<PaymentSettlementEntity, UUID> {
}
