package com.finflow.chapter370.service;

import com.finflow.chapter370.entity.OutboxEvent;
import com.finflow.chapter370.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Background worker polling the outbox table and dispatching pending events
 * to Kafka / Message Broker with at-least-once delivery guarantees.
 */
@Service
public class OutboxPublisherWorker {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisherWorker.class);

    private final OutboxEventRepository outboxEventRepository;

    public OutboxPublisherWorker(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public int publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findTop50ByStatusOrderByCreatedAtAsc("PENDING");
        if (pendingEvents.isEmpty()) {
            return 0;
        }

        log.debug("[OutboxPoller] Found {} pending events to publish", pendingEvents.size());

        int publishedCount = 0;
        for (OutboxEvent event : pendingEvents) {
            try {
                // Simulate publishing event to Kafka broker
                simulateKafkaPublish(event);

                // Mark published within transaction
                event.markPublished();
                outboxEventRepository.save(event);
                publishedCount++;
            } catch (Exception e) {
                log.error("[OutboxPoller] Failed to dispatch outbox event '{}'", event.getId(), e);
                event.markFailed();
                outboxEventRepository.save(event);
            }
        }

        log.info("[OutboxPoller] Successfully published {}/{} outbox events to message broker.",
                publishedCount, pendingEvents.size());

        return publishedCount;
    }

    private void simulateKafkaPublish(OutboxEvent event) {
        log.info("[KafkaProducer] Dispatched event to topic 'finflow.{}.events' | ID: {} | Type: {} | Payload: {}",
                event.getAggregateType().toLowerCase(), event.getId(), event.getEventType(), event.getPayload());
    }
}
