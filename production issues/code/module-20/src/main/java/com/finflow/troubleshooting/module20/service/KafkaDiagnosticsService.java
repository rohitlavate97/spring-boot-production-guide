package com.finflow.troubleshooting.module20.service;

import com.finflow.troubleshooting.module20.model.PaymentEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class KafkaDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(KafkaDiagnosticsService.class);

    public record PollBudgetResult(
            long maxPollIntervalMs,
            long p99MessageProcessingTimeMs,
            int configuredMaxPollRecords,
            int recommendedMaxPollRecords,
            long estimatedBatchProcessingTimeMs,
            String safetyStatus,
            List<String> warnings
    ) {}

    public record DeadLetterRecord(
            String topic,
            Object payload,
            String reason,
            long timestamp
    ) {}

    private final Queue<PaymentEvent> paymentEventsQueue = new ConcurrentLinkedQueue<>();
    private final Queue<DeadLetterRecord> deadLetterQueue = new ConcurrentLinkedQueue<>();

    private final AtomicLong messagesProduced = new AtomicLong(0);
    private final AtomicLong messagesConsumed = new AtomicLong(0);
    private final AtomicLong poisonPillsEncountered = new AtomicLong(0);
    private final AtomicLong dltRoutedCount = new AtomicLong(0);

    public PollBudgetResult calculatePollBudget(long maxPollIntervalMs, long p99ProcessingTimeMs, int configuredMaxPollRecords) {
        double safetyFactor = 0.70; // 30% headroom for GC pauses and network blips
        long availableTimeMs = (long) (maxPollIntervalMs * safetyFactor);
        int recommendedRecords = (int) Math.max(1, availableTimeMs / Math.max(1, p99ProcessingTimeMs));
        long estimatedBatchTime = configuredMaxPollRecords * p99ProcessingTimeMs;

        List<String> warnings = new ArrayList<>();
        String safetyStatus;

        if (estimatedBatchTime >= maxPollIntervalMs) {
            safetyStatus = "CRITICAL_REBALANCE_STORM_RISK";
            warnings.add("Batch processing time (" + estimatedBatchTime + "ms) EXCEEDS max.poll.interval.ms ("
                    + maxPollIntervalMs + "ms)! Kafka broker will mark consumer DEAD and trigger continuous group rebalances.");
        } else if (estimatedBatchTime >= availableTimeMs) {
            safetyStatus = "WARNING_HIGH_REBALANCE_RISK";
            warnings.add("Batch processing time (" + estimatedBatchTime + "ms) leaves under 30% headroom. GC pause or DB latency spike will cause rebalance.");
        } else {
            safetyStatus = "SAFE_POLL_BUDGET";
        }

        return new PollBudgetResult(
                maxPollIntervalMs,
                p99ProcessingTimeMs,
                configuredMaxPollRecords,
                recommendedRecords,
                estimatedBatchTime,
                safetyStatus,
                warnings
        );
    }

    public void producePayment(PaymentEvent event) {
        paymentEventsQueue.add(event);
        messagesProduced.incrementAndGet();
        log.info("[KAFKA PRODUCED] Payment {} on topic payment-events (Total produced: {})",
                event.transactionId(), messagesProduced.get());
    }

    public void producePoisonPill(String corruptedPayload) {
        poisonPillsEncountered.incrementAndGet();
        dltRoutedCount.incrementAndGet();
        DeadLetterRecord dlt = new DeadLetterRecord(
                "payment-events.DLT",
                corruptedPayload,
                "DeserializationException: Malformed JSON payload cannot be parsed to PaymentEvent.class",
                System.currentTimeMillis()
        );
        deadLetterQueue.add(dlt);
        log.warn("[POISON PILL RECOVERED] Routed malformed message to {} without blocking partition: {}",
                dlt.topic(), corruptedPayload);
    }

    public PaymentEvent consumeNextPayment() {
        PaymentEvent event = paymentEventsQueue.poll();
        if (event != null) {
            messagesConsumed.incrementAndGet();
            log.info("[KAFKA CONSUMED] Settled payment {} (Total consumed: {})",
                    event.transactionId(), messagesConsumed.get());
        }
        return event;
    }

    public long getCurrentConsumerLag() {
        return Math.max(0, messagesProduced.get() - messagesConsumed.get());
    }

    public void clear() {
        paymentEventsQueue.clear();
        deadLetterQueue.clear();
        messagesProduced.set(0);
        messagesConsumed.set(0);
        poisonPillsEncountered.set(0);
        dltRoutedCount.set(0);
    }

    public Map<String, Object> getDiagnosticsStats() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("messagesProduced", messagesProduced.get());
        stats.put("messagesConsumed", messagesConsumed.get());
        stats.put("currentConsumerLag", getCurrentConsumerLag());
        stats.put("poisonPillsEncountered", poisonPillsEncountered.get());
        stats.put("dltRoutedCount", dltRoutedCount.get());
        stats.put("dltQueueSize", deadLetterQueue.size());
        return stats;
    }

    public List<DeadLetterRecord> getDeadLetterRecords() {
        return new ArrayList<>(deadLetterQueue);
    }
}
