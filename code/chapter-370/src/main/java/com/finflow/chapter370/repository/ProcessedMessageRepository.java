package com.finflow.chapter370.repository;

import com.finflow.chapter370.entity.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage, Long> {
    Optional<ProcessedMessage> findByMessageIdAndConsumerGroup(String messageId, String consumerGroup);
    boolean existsByMessageIdAndConsumerGroup(String messageId, String consumerGroup);
}
