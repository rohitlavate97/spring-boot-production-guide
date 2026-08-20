package com.finflow.chapter180.repository;

import com.finflow.chapter180.domain.TransferTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransferTaskRepository extends JpaRepository<TransferTask, UUID> {

    @Query(value = "SELECT * FROM transfer_tasks WHERE status = 'PENDING' ORDER BY created_at ASC LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<TransferTask> fetchPendingTasksSkipLocked(@Param("limit") int limit);

    List<TransferTask> findAllByStatus(String status);
}
