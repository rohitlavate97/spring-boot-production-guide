package com.finflow.chapter160.repository;

import com.finflow.chapter160.domain.IdentitySettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IdentitySettlementRepository extends JpaRepository<IdentitySettlementRecord, Long> {
    List<IdentitySettlementRecord> findAllByBatchId(String batchId);
}
