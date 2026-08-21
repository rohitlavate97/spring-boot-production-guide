package com.finflow.chapter370.controller;

import com.finflow.chapter370.entity.SagaInstance;
import com.finflow.chapter370.repository.OutboxEventRepository;
import com.finflow.chapter370.service.OutboxPublisherWorker;
import com.finflow.chapter370.service.SagaOrchestrator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutSagaController {

    private final SagaOrchestrator sagaOrchestrator;
    private final OutboxPublisherWorker outboxPublisherWorker;
    private final OutboxEventRepository outboxEventRepository;

    public CheckoutSagaController(SagaOrchestrator sagaOrchestrator,
                                  OutboxPublisherWorker outboxPublisherWorker,
                                  OutboxEventRepository outboxEventRepository) {
        this.sagaOrchestrator = sagaOrchestrator;
        this.outboxPublisherWorker = outboxPublisherWorker;
        this.outboxEventRepository = outboxEventRepository;
    }

    @PostMapping("/saga/start")
    public ResponseEntity<SagaInstance> startCheckoutSaga(
            @RequestParam String orderId,
            @RequestParam(defaultValue = "MERCHANT-001") String merchantId,
            @RequestParam(defaultValue = "150.00") BigDecimal amount,
            @RequestParam(defaultValue = "USD") String currency,
            @RequestParam(defaultValue = "false") boolean simulateLedgerFailure) {

        SagaInstance saga = sagaOrchestrator.executeCheckoutSaga(
                orderId, merchantId, amount, currency, simulateLedgerFailure);
        return ResponseEntity.ok(saga);
    }

    @GetMapping("/saga/{sagaId}")
    public ResponseEntity<SagaInstance> getSaga(@PathVariable String sagaId) {
        return ResponseEntity.ok(sagaOrchestrator.getSaga(sagaId));
    }

    @GetMapping("/outbox/pending-count")
    public ResponseEntity<Map<String, Object>> getPendingOutboxCount() {
        long count = outboxEventRepository.countByStatus("PENDING");
        return ResponseEntity.ok(Map.of(
                "pendingEvents", count,
                "status", "OK"
        ));
    }

    @PostMapping("/outbox/flush")
    public ResponseEntity<Map<String, Object>> flushOutbox() {
        int published = outboxPublisherWorker.publishPendingEvents();
        return ResponseEntity.ok(Map.of(
                "publishedCount", published,
                "status", "FLUSHED"
        ));
    }
}
