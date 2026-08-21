package com.finflow.chapter370.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processed_messages", indexes = {
        @Index(name = "idx_processed_msg_key", columnList = "messageId, consumerGroup", unique = true)
})
public class ProcessedMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 128)
    private String messageId;

    @Column(nullable = false, length = 64)
    private String consumerGroup;

    @Column(nullable = false)
    private Instant processedAt;

    public ProcessedMessage() {
        this.processedAt = Instant.now();
    }

    public ProcessedMessage(String messageId, String consumerGroup) {
        this.messageId = messageId;
        this.consumerGroup = consumerGroup;
        this.processedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getMessageId() {
        return messageId;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}
