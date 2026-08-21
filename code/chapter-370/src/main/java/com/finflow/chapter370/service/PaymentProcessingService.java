package com.finflow.chapter370.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.chapter370.entity.OutboxEvent;
import com.finflow.chapter370.entity.PaymentOrder;
import com.finflow.chapter370.repository.OutboxEventRepository;
import com.finflow.chapter370.repository.PaymentOrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;

/**
 * Service demonstrating the Transactional Outbox Pattern.
 * Saves domain state (PaymentOrder) and event record (OutboxEvent)
 * within the EXACT same ACID database transaction boundary.
 */
@Service
public class PaymentProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentProcessingService.class);

    private final PaymentOrderRepository paymentOrderRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public PaymentProcessingService(PaymentOrderRepository paymentOrderRepository,
                                    OutboxEventRepository outboxEventRepository,
                                    ObjectMapper objectMapper) {
        this.paymentOrderRepository = paymentOrderRepository;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public PaymentOrder authorizePayment(String orderId, String merchantId, BigDecimal amount, String currency) {
        log.info("[PaymentService] Authorizing payment for order '{}' | Amount: {} {}", orderId, amount, currency);

        // 1. Mutate Domain Entity
        PaymentOrder order = new PaymentOrder(orderId, merchantId, amount, currency);
        order.setStatus("AUTHORIZED");
        paymentOrderRepository.save(order);

        // 2. Construct Outbox Event Payload
        Map<String, Object> eventPayload = Map.of(
                "orderId", orderId,
                "merchantId", merchantId,
                "amount", amount,
                "currency", currency,
                "status", "AUTHORIZED",
                "timestamp", System.currentTimeMillis()
        );

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(eventPayload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize outbox event payload", e);
        }

        // 3. Insert Outbox Event in the SAME ACID Transaction
        OutboxEvent outboxEvent = new OutboxEvent(
                "PAYMENT_ORDER",
                orderId,
                "PAYMENT_AUTHORIZED",
                jsonPayload
        );
        outboxEventRepository.save(outboxEvent);

        log.info("[OutboxPattern] Transaction committed! PaymentOrder saved and OutboxEvent '{}' created atomically.",
                outboxEvent.getId());

        return order;
    }

    @Transactional
    public PaymentOrder compensatePayment(String orderId) {
        log.warn("[SagaCompensation] Initiating compensating reversal for payment order '{}'", orderId);

        PaymentOrder order = paymentOrderRepository.findByOrderId(orderId)
                .orElseThrow(() -> new IllegalStateException("Order not found for compensation: " + orderId));

        // Reversal state transition
        order.setStatus("REVERSED");
        paymentOrderRepository.save(order);

        // Compensation Outbox event
        Map<String, Object> eventPayload = Map.of(
                "orderId", orderId,
                "status", "REVERSED",
                "reason", "Saga rollback: downstream ledger failed",
                "timestamp", System.currentTimeMillis()
        );

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(eventPayload);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize compensation outbox event", e);
        }

        OutboxEvent compensationEvent = new OutboxEvent(
                "PAYMENT_ORDER",
                orderId,
                "PAYMENT_REVERSED",
                jsonPayload
        );
        outboxEventRepository.save(compensationEvent);

        log.info("[SagaCompensation] Payment '{}' REVERSED and compensation outbox event '{}' queued.",
                orderId, compensationEvent.getId());

        return order;
    }
}
