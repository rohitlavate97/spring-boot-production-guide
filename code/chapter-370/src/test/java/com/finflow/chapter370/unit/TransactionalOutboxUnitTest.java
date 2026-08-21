package com.finflow.chapter370.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.chapter370.entity.OutboxEvent;
import com.finflow.chapter370.entity.PaymentOrder;
import com.finflow.chapter370.repository.OutboxEventRepository;
import com.finflow.chapter370.repository.PaymentOrderRepository;
import com.finflow.chapter370.repository.ProcessedMessageRepository;
import com.finflow.chapter370.service.IdempotentConsumerService;
import com.finflow.chapter370.service.OutboxPublisherWorker;
import com.finflow.chapter370.service.PaymentProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
public class TransactionalOutboxUnitTest {

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private ProcessedMessageRepository processedMessageRepository;

    private PaymentProcessingService paymentProcessingService;
    private OutboxPublisherWorker outboxPublisherWorker;
    private IdempotentConsumerService idempotentConsumerService;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        paymentProcessingService = new PaymentProcessingService(
                paymentOrderRepository, outboxEventRepository, objectMapper);
        outboxPublisherWorker = new OutboxPublisherWorker(outboxEventRepository);
        idempotentConsumerService = new IdempotentConsumerService(processedMessageRepository);
    }

    @Test
    void testPaymentAndOutboxEventCommittedAtomically() {
        PaymentOrder order = paymentProcessingService.authorizePayment(
                "ORD-TEST-001", "MERCHANT-A", BigDecimal.valueOf(250.00), "USD");

        // 1. Verify Payment Order
        assertThat(order).isNotNull();
        assertThat(order.getStatus()).isEqualTo("AUTHORIZED");
        assertThat(paymentOrderRepository.findByOrderId("ORD-TEST-001")).isPresent();

        // 2. Verify Outbox Event created
        List<OutboxEvent> events = outboxEventRepository.findByAggregateId("ORD-TEST-001");
        assertThat(events).hasSize(1);
        OutboxEvent event = events.get(0);
        assertThat(event.getEventType()).isEqualTo("PAYMENT_AUTHORIZED");
        assertThat(event.getStatus()).isEqualTo("PENDING");
        assertThat(event.getPayload()).contains("ORD-TEST-001");
    }

    @Test
    void testOutboxWorkerPublishesPendingEvents() {
        paymentProcessingService.authorizePayment(
                "ORD-TEST-002", "MERCHANT-B", BigDecimal.valueOf(100.00), "USD");

        assertThat(outboxEventRepository.countByStatus("PENDING")).isGreaterThanOrEqualTo(1);

        // Run Outbox Poller
        int published = outboxPublisherWorker.publishPendingEvents();
        assertThat(published).isGreaterThanOrEqualTo(1);

        // Verify Status transition to PUBLISHED
        List<OutboxEvent> events = outboxEventRepository.findByAggregateId("ORD-TEST-002");
        assertThat(events.get(0).getStatus()).isEqualTo("PUBLISHED");
        assertThat(events.get(0).getProcessedAt()).isNotNull();
    }

    @Test
    void testIdempotentConsumerSuppressesDuplicates() {
        AtomicInteger executionCounter = new AtomicInteger(0);
        String messageId = "MSG-UUID-998877";
        String group = "ledger-consumer-group";

        // First delivery: should process
        boolean firstRun = idempotentConsumerService.processMessage(messageId, group, executionCounter::incrementAndGet);
        assertThat(firstRun).isTrue();
        assertThat(executionCounter.get()).isEqualTo(1);

        // Redelivery / Duplicate delivery: should be suppressed
        boolean duplicateRun = idempotentConsumerService.processMessage(messageId, group, executionCounter::incrementAndGet);
        assertThat(duplicateRun).isFalse();
        assertThat(executionCounter.get()).isEqualTo(1); // Counter did not change!
    }
}
