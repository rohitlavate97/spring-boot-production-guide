package com.finflow.troubleshooting.module20.controller;

import com.finflow.troubleshooting.module20.model.PaymentEvent;
import com.finflow.troubleshooting.module20.service.KafkaDiagnosticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/kafka")
public class KafkaDiagnosticsController {

    private final KafkaDiagnosticsService diagnosticsService;

    public KafkaDiagnosticsController(KafkaDiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(diagnosticsService.getDiagnosticsStats());
    }

    @PostMapping("/produce")
    public ResponseEntity<Map<String, Object>> producePayment(
            @RequestParam(defaultValue = "ACC-998877") String accountId,
            @RequestParam(defaultValue = "500.00") double amount,
            @RequestParam(defaultValue = "USD") String currency
    ) {
        String txnId = "TXN-" + UUID.randomUUID().toString().substring(0, 8);
        PaymentEvent event = PaymentEvent.of(txnId, accountId, amount, currency);
        diagnosticsService.producePayment(event);

        return ResponseEntity.ok(Map.of(
                "status", "PRODUCED",
                "topic", "payment-events",
                "event", event,
                "currentLag", diagnosticsService.getCurrentConsumerLag()
        ));
    }

    @PostMapping("/produce-poison-pill")
    public ResponseEntity<Map<String, Object>> producePoisonPill(
            @RequestParam(defaultValue = "{\"corrupted\":\"INVALID_BINARY_BYTES_###\"}") String payload
    ) {
        diagnosticsService.producePoisonPill(payload);
        return ResponseEntity.ok(Map.of(
                "status", "POISON_PILL_HANDLED",
                "action", "ROUTED_TO_DLT",
                "dltTopic", "payment-events.DLT",
                "note", "ErrorHandlingDeserializer intercepted malformed message; partition consumption continues uninterrupted!"
        ));
    }

    @PostMapping("/consume")
    public ResponseEntity<Map<String, Object>> consumeNext() {
        PaymentEvent event = diagnosticsService.consumeNextPayment();
        if (event == null) {
            return ResponseEntity.ok(Map.of("status", "NO_MESSAGES_IN_QUEUE", "currentLag", 0));
        }
        return ResponseEntity.ok(Map.of(
                "status", "CONSUMED",
                "event", event,
                "remainingLag", diagnosticsService.getCurrentConsumerLag()
        ));
    }

    @GetMapping("/calculate-poll-budget")
    public ResponseEntity<KafkaDiagnosticsService.PollBudgetResult> calculatePollBudget(
            @RequestParam(defaultValue = "300000") long maxPollIntervalMs,
            @RequestParam(defaultValue = "500") long p99ProcessingTimeMs,
            @RequestParam(defaultValue = "500") int configuredMaxPollRecords
    ) {
        var result = diagnosticsService.calculatePollBudget(maxPollIntervalMs, p99ProcessingTimeMs, configuredMaxPollRecords);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/dlt-records")
    public ResponseEntity<Map<String, Object>> getDltRecords() {
        return ResponseEntity.ok(Map.of(
                "dltTopic", "payment-events.DLT",
                "totalCount", diagnosticsService.getDeadLetterRecords().size(),
                "records", diagnosticsService.getDeadLetterRecords()
        ));
    }

    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clear() {
        diagnosticsService.clear();
        return ResponseEntity.ok(Map.of("status", "CLEARED"));
    }
}
