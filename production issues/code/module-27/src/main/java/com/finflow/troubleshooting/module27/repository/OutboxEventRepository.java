package com.finflow.troubleshooting.module27.repository;

import com.finflow.troubleshooting.module27.model.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findByStatus(OutboxEvent.OutboxStatus status);
}
