package com.finflow.chapter370.repository;

import com.finflow.chapter370.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {

    List<OutboxEvent> findTop50ByStatusOrderByCreatedAtAsc(String status);

    long countByStatus(String status);

    List<OutboxEvent> findByAggregateId(String aggregateId);
}
