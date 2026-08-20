package com.finflow.chapter190.repository;

import com.finflow.chapter190.domain.PaymentConnectionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PaymentConnectionRecordRepository extends JpaRepository<PaymentConnectionRecord, UUID> {
    Optional<PaymentConnectionRecord> findByOrderRef(String orderRef);
}
