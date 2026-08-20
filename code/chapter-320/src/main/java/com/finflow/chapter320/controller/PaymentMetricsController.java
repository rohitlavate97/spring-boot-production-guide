package com.finflow.chapter320.controller;

import com.finflow.chapter320.domain.PaymentTransaction;
import com.finflow.chapter320.service.PaymentMetricsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentMetricsController {

    private final PaymentMetricsService metricsService;

    public PaymentMetricsController(PaymentMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @PostMapping("/process")
    public ResponseEntity<PaymentTransaction> processPayment(@RequestBody PaymentTransaction request) {
        if (request.getTransactionId() == null) {
            request.setTransactionId("TX-" + UUID.randomUUID().toString().substring(0, 8));
        }
        PaymentTransaction processed = metricsService.processPayment(request);
        return ResponseEntity.ok(processed);
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<Map<String, Object>> getDiagnostics() {
        return ResponseEntity.ok(Map.of(
                "inFlightRequests", metricsService.getInFlightRequests(),
                "status", "ACTIVE"
        ));
    }
}
