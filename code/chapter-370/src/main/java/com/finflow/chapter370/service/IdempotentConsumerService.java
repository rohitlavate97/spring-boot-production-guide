package com.finflow.chapter370.service;

import com.finflow.chapter370.entity.ProcessedMessage;
import com.finflow.chapter370.repository.ProcessedMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent consumer pattern implementation.
 * Ensures exactly-once business processing semantics at the subscriber end.
 */
@Service
public class IdempotentConsumerService {

    private static final Logger log = LoggerFactory.getLogger(IdempotentConsumerService.class);

    private final ProcessedMessageRepository processedMessageRepository;

    public IdempotentConsumerService(ProcessedMessageRepository processedMessageRepository) {
        this.processedMessageRepository = processedMessageRepository;
    }

    @Transactional
    public boolean processMessage(String messageId, String consumerGroup, Runnable businessLogic) {
        if (processedMessageRepository.existsByMessageIdAndConsumerGroup(messageId, consumerGroup)) {
            log.warn("[IdempotencyDeduplicator] Duplicate message detected! MessageId: '{}' for Group: '{}'. Skipping.",
                    messageId, consumerGroup);
            return false;
        }

        // Execute domain logic
        businessLogic.run();

        // Record processed message in the same transaction
        processedMessageRepository.save(new ProcessedMessage(messageId, consumerGroup));
        log.info("[IdempotencyDeduplicator] Successfully processed and recorded MessageId: '{}' for Group: '{}'",
                messageId, consumerGroup);

        return true;
    }
}
