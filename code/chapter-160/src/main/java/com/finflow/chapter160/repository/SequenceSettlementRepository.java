package com.finflow.chapter160.repository;

import com.finflow.chapter160.domain.SequenceSettlementRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SequenceSettlementRepository extends JpaRepository<SequenceSettlementRecord, Long> {
    List<SequenceSettlementRecord> findAllByBatchId(String batchId);
}
