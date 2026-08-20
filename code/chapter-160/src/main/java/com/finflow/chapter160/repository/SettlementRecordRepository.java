package com.finflow.chapter160.repository;

import com.finflow.chapter160.domain.SettlementRecord;
import com.finflow.chapter160.domain.SettlementStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface SettlementRecordRepository extends JpaRepository<SettlementRecord, UUID> {
    List<SettlementRecord> findAllByBatchId(String batchId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE SettlementRecord s SET s.status = :newStatus WHERE s.batchId = :batchId AND s.status = :oldStatus")
    int bulkUpdateStatusByBatchId(@Param("batchId") String batchId,
                                  @Param("oldStatus") SettlementStatus oldStatus,
                                  @Param("newStatus") SettlementStatus newStatus);
}
