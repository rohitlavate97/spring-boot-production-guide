package com.finflow.chapter260.correct;

import com.finflow.chapter260.domain.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PaymentEventConsumerService {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumerService.class);

    private final Set<String> processedEventIds = ConcurrentHashMap.newKeySet();
    private final AtomicInteger processedCount = new AtomicInteger(0);
    private final AtomicInteger duplicateCount = new AtomicInteger(0);
    private final AtomicInteger failureCount = new AtomicInteger(0);

    @KafkaListener(topics = "payment.events", groupId = "finflow-payment-group", containerFactory = "kafkaListenerContainerFactory")
    public void consume(PaymentEvent event, Acknowledgment ack) {
        log.info("Received PaymentEvent: {}", event);

        // Consumer-Side Idempotency Deduplication Check
        if (processedEventIds.contains(event.getEventId())) {
            log.warn("Duplicate PaymentEvent detected (idempotency triggered): eventId={}", event.getEventId());
            duplicateCount.incrementAndGet();
            ack.acknowledge(); // Acknowledge to advance offset
            return;
        }

        // Poison Pill Simulation -> Throws exception to trigger DefaultErrorHandler & DLT routing
        if ("POISON_PILL".equals(event.getStatus())) {
            failureCount.incrementAndGet();
            throw new RuntimeException("Simulated Poison Pill Processing Failure for event: " + event.getEventId());
        }

        // Process Business Logic (e.g. Ledger posting, merchant settlement)
        processedEventIds.add(event.getEventId());
        processedCount.incrementAndGet();

        // Manual Immediate Acknowledgment
        ack.acknowledge();
    }

    public int getProcessedCount() { return processedCount.get(); }
    public int getDuplicateCount() { return duplicateCount.get(); }
    public int getFailureCount() { return failureCount.get(); }
    public void reset() {
        processedEventIds.clear();
        processedCount.set(0);
        duplicateCount.set(0);
        failureCount.set(0);
    }
}
