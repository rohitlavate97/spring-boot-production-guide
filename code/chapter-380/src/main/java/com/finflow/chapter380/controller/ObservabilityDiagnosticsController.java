package com.finflow.chapter380.controller;

import com.finflow.chapter380.model.DiagnosticSnapshot;
import com.finflow.chapter380.model.IncidentReport;
import com.finflow.chapter380.service.SreRunbookExecutor;
import com.finflow.chapter380.service.StructuredLoggingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/observability")
public class ObservabilityDiagnosticsController {

    private final SreRunbookExecutor runbookExecutor;
    private final StructuredLoggingService loggingService;

    public ObservabilityDiagnosticsController(SreRunbookExecutor runbookExecutor,
                                              StructuredLoggingService loggingService) {
        this.runbookExecutor = runbookExecutor;
        this.loggingService = loggingService;
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<DiagnosticSnapshot> getDiagnostics(
            @RequestParam(defaultValue = "0.2") double errorRate,
            @RequestParam(defaultValue = "45.0") double p99Latency) {
        DiagnosticSnapshot snapshot = runbookExecutor.captureDiagnosticSnapshot(errorRate, p99Latency);
        return ResponseEntity.ok(snapshot);
    }

    @PostMapping("/runbook/triage")
    public ResponseEntity<IncidentReport> triageIncident(
            @RequestParam(defaultValue = "INC-2026-001") String incidentId,
            @RequestParam(defaultValue = "6.5") double errorRate,
            @RequestParam(defaultValue = "2400.0") double p99Latency) {
        IncidentReport report = runbookExecutor.executeTriageRunbook(incidentId, errorRate, p99Latency);
        return ResponseEntity.ok(report);
    }

    @PostMapping("/log-payment")
    public ResponseEntity<Map<String, Object>> logPayment(
            @RequestParam(defaultValue = "ORD-OBS-001") String orderId,
            @RequestParam(defaultValue = "MERCHANT-88") String merchantId,
            @RequestParam(defaultValue = "250.00") BigDecimal amount,
            @RequestParam(defaultValue = "4111-2222-3333-4444") String cardPan,
            @RequestParam(defaultValue = "true") boolean success,
            @RequestParam(defaultValue = "65") long durationMs) {

        loggingService.logPaymentTransaction(orderId, merchantId, amount, cardPan, success, durationMs);

        return ResponseEntity.ok(Map.of(
                "status", "LOGGED",
                "orderId", orderId,
                "note", "Transaction logged with PCI-DSS PII masking and recorded in Prometheus metrics"
        ));
    }
}
