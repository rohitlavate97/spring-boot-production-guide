package com.finflow.troubleshooting.module17.controller;

import com.finflow.troubleshooting.module17.service.PodLifecycleService;
import com.finflow.troubleshooting.module17.service.SimulatedDownstreamDependencyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class PodLifecycleController {

    private final PodLifecycleService lifecycleService;
    private final SimulatedDownstreamDependencyService downstreamService;

    public PodLifecycleController(PodLifecycleService lifecycleService,
                                  SimulatedDownstreamDependencyService downstreamService) {
        this.lifecycleService = lifecycleService;
        this.downstreamService = downstreamService;
    }

    @GetMapping("/lifecycle/status")
    public ResponseEntity<Map<String, Object>> getStatus() {
        return ResponseEntity.ok(lifecycleService.getLifecycleStatus());
    }

    @PostMapping("/lifecycle/drain-traffic")
    public ResponseEntity<Map<String, Object>> drainTraffic() {
        lifecycleService.drainTraffic();
        return ResponseEntity.ok(Map.of(
                "action", "DRAIN_TRAFFIC",
                "readinessState", "REFUSING_TRAFFIC",
                "message", "Pod readiness set to REFUSING_TRAFFIC. Kubelet will remove pod from Endpoints."
        ));
    }

    @PostMapping("/lifecycle/accept-traffic")
    public ResponseEntity<Map<String, Object>> acceptTraffic() {
        lifecycleService.acceptTraffic();
        return ResponseEntity.ok(Map.of(
                "action", "ACCEPT_TRAFFIC",
                "readinessState", "ACCEPTING_TRAFFIC",
                "message", "Pod readiness restored to ACCEPTING_TRAFFIC."
        ));
    }

    @PostMapping("/lifecycle/break-liveness")
    public ResponseEntity<Map<String, Object>> breakLiveness() {
        lifecycleService.breakLiveness();
        return ResponseEntity.ok(Map.of(
                "action", "BREAK_LIVENESS",
                "livenessState", "BROKEN",
                "message", "LivenessState set to BROKEN. Kubelet liveness probe will fail and restart container."
        ));
    }

    @PostMapping("/lifecycle/restore-liveness")
    public ResponseEntity<Map<String, Object>> restoreLiveness() {
        lifecycleService.restoreLiveness();
        return ResponseEntity.ok(Map.of(
                "action", "RESTORE_LIVENESS",
                "livenessState", "CORRECT",
                "message", "LivenessState restored to CORRECT."
        ));
    }

    @PostMapping("/lifecycle/simulate-downstream-failure")
    public ResponseEntity<Map<String, Object>> simulateDownstreamFailure() {
        downstreamService.setDatabaseFailure(true);
        return ResponseEntity.ok(Map.of(
                "action", "SIMULATE_DOWNSTREAM_FAILURE",
                "databaseReachable", false,
                "note", "Readiness will turn DOWN (503), but Liveness remains UP (200) to prevent restart storm!"
        ));
    }

    @PostMapping("/lifecycle/restore-downstream")
    public ResponseEntity<Map<String, Object>> restoreDownstream() {
        downstreamService.setDatabaseFailure(false);
        return ResponseEntity.ok(Map.of(
                "action", "RESTORE_DOWNSTREAM",
                "databaseReachable", true
        ));
    }

    @PostMapping("/orders/checkout")
    public ResponseEntity<Map<String, Object>> processCheckout(@RequestParam(defaultValue = "100") long delayMs) {
        lifecycleService.registerInflightStart();
        try {
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
            String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8);
            return ResponseEntity.ok(Map.of(
                    "status", "SETTLED",
                    "transactionId", transactionId,
                    "amount", 250.00,
                    "currency", "USD"
            ));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(500).body(Map.of("status", "INTERRUPTED", "error", e.getMessage()));
        } finally {
            lifecycleService.registerInflightEnd();
        }
    }
}
