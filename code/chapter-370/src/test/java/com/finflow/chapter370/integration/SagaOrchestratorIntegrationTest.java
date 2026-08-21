package com.finflow.chapter370.integration;

import com.finflow.chapter370.Chapter370Application;
import com.finflow.chapter370.entity.OutboxEvent;
import com.finflow.chapter370.entity.PaymentOrder;
import com.finflow.chapter370.entity.SagaInstance;
import com.finflow.chapter370.repository.OutboxEventRepository;
import com.finflow.chapter370.repository.PaymentOrderRepository;
import com.finflow.chapter370.service.SagaOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Chapter370Application.class)
@AutoConfigureMockMvc
public class SagaOrchestratorIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SagaOrchestrator sagaOrchestrator;

    @Autowired
    private PaymentOrderRepository paymentOrderRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Test
    void testHappyPathCheckoutSagaCompletes() {
        String orderId = "ORD-SAGA-SUCCESS-001";
        SagaInstance saga = sagaOrchestrator.executeCheckoutSaga(
                orderId, "MERCHANT-1", BigDecimal.valueOf(500.00), "USD", false);

        assertThat(saga.getStatus()).isEqualTo("COMPLETED");
        assertThat(saga.getCurrentStep()).isEqualTo("COMPLETE_ORDER");

        PaymentOrder payment = paymentOrderRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo("AUTHORIZED");

        List<OutboxEvent> events = outboxEventRepository.findByAggregateId(orderId);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("PAYMENT_AUTHORIZED");
    }

    @Test
    void testLedgerFailureTriggersCompensatingRollback() {
        String orderId = "ORD-SAGA-FAIL-002";
        SagaInstance saga = sagaOrchestrator.executeCheckoutSaga(
                orderId, "MERCHANT-2", BigDecimal.valueOf(300.00), "USD", true);

        // Saga should be in compensated failure state
        assertThat(saga.getStatus()).isEqualTo("COMPENSATED_FAILED");
        assertThat(saga.getCurrentStep()).isEqualTo("COMPENSATION_COMPLETE");

        // Payment must be reversed!
        PaymentOrder payment = paymentOrderRepository.findByOrderId(orderId).orElseThrow();
        assertThat(payment.getStatus()).isEqualTo("REVERSED");

        // Both authorization event and reversal event should exist in outbox!
        List<OutboxEvent> events = outboxEventRepository.findByAggregateId(orderId);
        assertThat(events).hasSize(2);
        assertThat(events.stream().anyMatch(e -> "PAYMENT_AUTHORIZED".equals(e.getEventType()))).isTrue();
        assertThat(events.stream().anyMatch(e -> "PAYMENT_REVERSED".equals(e.getEventType()))).isTrue();
    }

    @Test
    void testCheckoutSagaEndpoints() throws Exception {
        mockMvc.perform(post("/api/v1/checkout/saga/start?orderId=ORD-REST-001&amount=99.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-REST-001"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        mockMvc.perform(get("/api/v1/checkout/outbox/pending-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.pendingEvents").isNumber());

        mockMvc.perform(post("/api/v1/checkout/outbox/flush"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FLUSHED"));
    }
}
