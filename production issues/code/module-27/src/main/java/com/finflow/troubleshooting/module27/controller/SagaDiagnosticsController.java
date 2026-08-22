package com.finflow.troubleshooting.module27.controller;

import com.finflow.troubleshooting.module27.model.OrderEntity;
import com.finflow.troubleshooting.module27.model.OutboxEvent;
import com.finflow.troubleshooting.module27.model.SagaInstance;
import com.finflow.troubleshooting.module27.repository.OutboxEventRepository;
import com.finflow.troubleshooting.module27.repository.SagaInstanceRepository;
import com.finflow.troubleshooting.module27.saga.PaymentSagaOrchestrator;
import com.finflow.troubleshooting.module27.service.TransactionalOutboxService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/saga")
public class SagaDiagnosticsController {

    private final PaymentSagaOrchestrator sagaOrchestrator;
    private final TransactionalOutboxService outboxService;
    private final SagaInstanceRepository sagaRepository;
    private final OutboxEventRepository outboxRepository;

    public SagaDiagnosticsController(PaymentSagaOrchestrator sagaOrchestrator,
                                     TransactionalOutboxService outboxService,
                                     SagaInstanceRepository sagaRepository,
                                     OutboxEventRepository outboxRepository) {
        this.sagaOrchestrator = sagaOrchestrator;
        this.outboxService = outboxService;
        this.sagaRepository = sagaRepository;
        this.outboxRepository = outboxRepository;
    }

    @PostMapping("/execute")
    public ResponseEntity<PaymentSagaOrchestrator.SagaExecutionResult> executeSaga(
            @RequestParam(defaultValue = "ACC-US-5500") String accountId,
            @RequestParam(defaultValue = "2500.00") double amount,
            @RequestParam(defaultValue = "false") boolean simulateFxFailure
    ) {
        var result = sagaOrchestrator.executePaymentSaga(accountId, amount, simulateFxFailure);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/instances")
    public ResponseEntity<List<SagaInstance>> getSagaInstances() {
        return ResponseEntity.ok(sagaRepository.findAll());
    }

    @GetMapping("/wallet-balance")
    public ResponseEntity<Map<String, Object>> getWalletBalance(@RequestParam(defaultValue = "ACC-US-5500") String accountId) {
        return ResponseEntity.ok(Map.of(
                "accountId", accountId,
                "currentBalance", sagaOrchestrator.getWalletBalance(accountId)
        ));
    }

    @PostMapping("/outbox/create-order")
    public ResponseEntity<OrderEntity> createOrderOutbox(
            @RequestParam(defaultValue = "ACC-US-5500") String accountId,
            @RequestParam(defaultValue = "500.00") BigDecimal amount
    ) {
        OrderEntity order = outboxService.createOrderWithOutboxEvent(accountId, amount);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/outbox/publish")
    public ResponseEntity<Map<String, Object>> publishOutbox() {
        int published = outboxService.publishPendingOutboxEvents();
        return ResponseEntity.ok(Map.of("publishedEventsCount", published));
    }

    @GetMapping("/outbox/events")
    public ResponseEntity<List<OutboxEvent>> getOutboxEvents() {
        return ResponseEntity.ok(outboxRepository.findAll());
    }
}
