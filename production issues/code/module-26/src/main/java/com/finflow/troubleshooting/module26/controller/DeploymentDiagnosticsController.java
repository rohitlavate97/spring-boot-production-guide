package com.finflow.troubleshooting.module26.controller;

import com.finflow.troubleshooting.module26.service.CanaryTrafficRouterService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/deploy")
public class DeploymentDiagnosticsController {

    private final CanaryTrafficRouterService routerService;

    public DeploymentDiagnosticsController(CanaryTrafficRouterService routerService) {
        this.routerService = routerService;
    }

    @GetMapping("/route")
    public ResponseEntity<CanaryTrafficRouterService.RoutingDecision> routeRequest(
            @RequestParam(defaultValue = "USR-998811") String userId,
            @CookieValue(value = "canary_affinity", required = false) String canaryCookie
    ) {
        var decision = routerService.routeRequest(userId, canaryCookie);
        return ResponseEntity.ok(decision);
    }

    @GetMapping("/canary-health")
    public ResponseEntity<CanaryTrafficRouterService.CanaryHealthReport> getCanaryHealth() {
        var report = routerService.evaluateCanaryHealth();
        return ResponseEntity.ok(report);
    }

    @PostMapping("/simulate-anomaly")
    public ResponseEntity<Map<String, Object>> simulateAnomaly(
            @RequestParam(defaultValue = "15") int failedCanaryRequests,
            @RequestParam(defaultValue = "450") long highLatencyMs
    ) {
        for (int i = 0; i < failedCanaryRequests; i++) {
            routerService.recordTraffic(true, true, highLatencyMs);
        }

        var report = routerService.evaluateCanaryHealth();
        return ResponseEntity.ok(Map.of(
                "injectedErrorsCount", failedCanaryRequests,
                "injectedLatencyMs", highLatencyMs,
                "resultingHealthDecision", report.decision(),
                "report", report
        ));
    }

    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> reset() {
        routerService.clear();
        return ResponseEntity.ok(Map.of("status", "CLEARED"));
    }
}
