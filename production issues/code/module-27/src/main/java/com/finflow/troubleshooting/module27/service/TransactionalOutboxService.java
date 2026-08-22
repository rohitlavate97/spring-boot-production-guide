package com.finflow.troubleshooting.module27.service;

import com.finflow.troubleshooting.module27.model.OrderEntity;
import com.finflow.troubleshooting.module27.model.OutboxEvent;
import com.finflow.troubleshooting.module27.repository.OrderRepository;
import com.finflow.troubleshooting.module27.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
public class TransactionalOutboxService {

    private static final Logger log = LoggerFactory.getLogger(TransactionalOutboxService.class);

    private final OrderRepository orderRepository;
    private final OutboxEventRepository outboxRepository;

    public TransactionalOutboxService(OrderRepository orderRepository, OutboxEventRepository outboxRepository) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * ✅ TRANSACTIONAL OUTBOX PATTERN:
     * Saves business entity (Order) AND OutboxEvent in the EXACT SAME database transaction.
     * Guarantees 0 dual-write inconsistency between DB and Kafka/Broker!
     */
    @Transactional
    public OrderEntity createOrderWithOutboxEvent(String accountId, BigDecimal amount) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8);
        OrderEntity order = new OrderEntity(orderId, accountId, amount, "CREATED");
        OrderEntity savedOrder = orderRepository.save(order);

        String payload = "{\"orderId\":\"" + orderId + "\",\"accountId\":\"" + accountId + "\",\"amount\":" + amount + "}";
        OutboxEvent event = new OutboxEvent("ORDER", orderId, "OrderCreatedEvent", payload);
        outboxRepository.save(event);

        log.info("[TRANSACTIONAL OUTBOX SUCCESS] Order {} and OutboxEvent {} saved in same ACID transaction!",
                orderId, event.getId());
        return savedOrder;
    }

    /**
     * Background Outbox Relay (Simulates CDC / Debezium / Scheduled Poller):
     * Dispatches PENDING events to message broker and updates status to PUBLISHED.
     */
    @Transactional
    public int publishPendingOutboxEvents() {
        List<OutboxEvent> pending = outboxRepository.findByStatus(OutboxEvent.OutboxStatus.PENDING);
        for (OutboxEvent event : pending) {
            // Simulated Kafka publish
            log.info("[OUTBOX DISPATCHED TO KAFKA] EventType={} AggregateId={} Payload={}",
                    event.getEventType(), event.getAggregateId(), event.getPayload());
            event.setStatus(OutboxEvent.OutboxStatus.PUBLISHED);
            outboxRepository.save(event);
        }
        return pending.size();
    }
}
