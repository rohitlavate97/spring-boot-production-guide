package com.finflow.chapter310.controller;

import com.finflow.chapter310.domain.PodHealthState;
import com.finflow.chapter310.service.KubernetesAvailabilityService;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentTrafficController {

    private final KubernetesAvailabilityService availabilityService;

    public PaymentTrafficController(KubernetesAvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/health")
    public ResponseEntity<PodHealthState> getHealth() {
        return ResponseEntity.ok(availabilityService.getPodHealthState());
    }

    @PostMapping("/readiness/refuse")
    public ResponseEntity<Map<String, String>> refuseTraffic(@RequestParam(defaultValue = "Manual graceful drain") String reason) {
        availabilityService.setReadiness(ReadinessState.REFUSING_TRAFFIC, reason);
        return ResponseEntity.ok(Map.of("status", "READINESS_SET_TO_REFUSING_TRAFFIC", "reason", reason));
    }

    @PostMapping("/readiness/accept")
    public ResponseEntity<Map<String, String>> acceptTraffic(@RequestParam(defaultValue = "Traffic restored") String reason) {
        availabilityService.setReadiness(ReadinessState.ACCEPTING_TRAFFIC, reason);
        return ResponseEntity.ok(Map.of("status", "READINESS_SET_TO_ACCEPTING_TRAFFIC", "reason", reason));
    }

    @PostMapping("/liveness/break")
    public ResponseEntity<Map<String, String>> breakLiveness(@RequestParam(defaultValue = "Fatal deadlock detected") String reason) {
        availabilityService.setLiveness(LivenessState.BROKEN, reason);
        return ResponseEntity.ok(Map.of("status", "LIVENESS_SET_TO_BROKEN", "reason", reason));
    }

    @PostMapping("/liveness/correct")
    public ResponseEntity<Map<String, String>> correctLiveness(@RequestParam(defaultValue = "State recovered") String reason) {
        availabilityService.setLiveness(LivenessState.CORRECT, reason);
        return ResponseEntity.ok(Map.of("status", "LIVENESS_SET_TO_CORRECT", "reason", reason));
    }
}
