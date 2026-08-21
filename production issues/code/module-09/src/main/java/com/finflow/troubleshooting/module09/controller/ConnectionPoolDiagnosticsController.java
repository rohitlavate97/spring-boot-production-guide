package com.finflow.troubleshooting.module09.controller;

import com.finflow.troubleshooting.module09.service.HikariPoolMetricsService;
import com.finflow.troubleshooting.module09.service.LeakSimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/pool")
public class ConnectionPoolDiagnosticsController {

    private final HikariPoolMetricsService metricsService;
    private final LeakSimulationService leakSimulationService;

    public ConnectionPoolDiagnosticsController(HikariPoolMetricsService metricsService,
                                               LeakSimulationService leakSimulationService) {
        this.metricsService = metricsService;
        this.leakSimulationService = leakSimulationService;
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getPoolMetrics() {
        return ResponseEntity.ok(metricsService.getPoolStatistics());
    }

    @PostMapping("/settle")
    public ResponseEntity<Map<String, Object>> settlePayment(@RequestParam BigDecimal amount,
                                                             @RequestParam(defaultValue = "0") long delayMs) {
        String txnId = leakSimulationService.settlePaymentHoldingConnection(amount, delayMs);
        return ResponseEntity.ok(Map.of("transactionId", txnId, "status", "SETTLED"));
    }

    @PostMapping("/leak-raw")
    public ResponseEntity<Map<String, String>> triggerRawLeak() throws SQLException {
        leakSimulationService.simulateUnclosedRawJdbcConnection();
        return ResponseEntity.ok(Map.of("message", "Triggered unclosed connection leak simulation"));
    }
}
