package com.finflow.troubleshooting.module27;

import com.finflow.troubleshooting.module27.model.OrderEntity;
import com.finflow.troubleshooting.module27.model.OutboxEvent;
import com.finflow.troubleshooting.module27.repository.OutboxEventRepository;
import com.finflow.troubleshooting.module27.service.TransactionalOutboxService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class TransactionalOutboxTest {

    @Autowired
    private TransactionalOutboxService outboxService;

    @Autowired
    private OutboxEventRepository outboxRepository;

    @Test
    @DisplayName("Creating order MUST atomically write both OrderEntity and OutboxEvent")
    void testAtomicOutboxCreation() {
        OrderEntity order = outboxService.createOrderWithOutboxEvent("ACC-101", BigDecimal.valueOf(750.00));

        assertThat(order.getId()).isNotNull();

        List<OutboxEvent> events = outboxRepository.findByStatus(OutboxEvent.OutboxStatus.PENDING);
        assertThat(events).anyMatch(e -> e.getAggregateId().equals(order.getId()) && e.getEventType().equals("OrderCreatedEvent"));
    }

    @Test
    @DisplayName("Publishing outbox events MUST transition status from PENDING to PUBLISHED")
    void testPublishOutboxEvents() {
        outboxService.createOrderWithOutboxEvent("ACC-102", BigDecimal.valueOf(1200.00));

        int publishedCount = outboxService.publishPendingOutboxEvents();
        assertThat(publishedCount).isGreaterThanOrEqualTo(1);

        List<OutboxEvent> pending = outboxRepository.findByStatus(OutboxEvent.OutboxStatus.PENDING);
        assertThat(pending).isEmpty();
    }
}
