package com.finflow.chapter140.correct;

import com.finflow.chapter140.domain.PaymentLedgerEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface PaymentLedgerRepository extends JpaRepository<PaymentLedgerEntity, UUID> {
    PaymentLedgerEntity findByEntryCode(String entryCode);
}
