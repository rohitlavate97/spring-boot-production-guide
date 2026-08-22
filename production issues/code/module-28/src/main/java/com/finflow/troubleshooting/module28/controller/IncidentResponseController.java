package com.finflow.troubleshooting.module28.controller;

import com.finflow.troubleshooting.module28.model.IncidentRecord;
import com.finflow.troubleshooting.module28.service.IncidentTriageEngine;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/incident")
public class IncidentResponseController {

    private final IncidentTriageEngine triageEngine;

    public IncidentResponseController(IncidentTriageEngine triageEngine) {
        this.triageEngine = triageEngine;
    }

    @PostMapping("/triage")
    public ResponseEntity<IncidentTriageEngine.TriageDecision> triageAlert(
            @RequestParam(defaultValue = "6.5") double errorRatePercent,
            @RequestParam(defaultValue = "2500.0") double p99LatencyMs,
            @RequestParam(defaultValue = "true") boolean isRevenueImpacting
    ) {
        var decision = triageEngine.triageAlert(errorRatePercent, p99LatencyMs, isRevenueImpacting);
        return ResponseEntity.ok(decision);
    }

    @GetMapping("/scenarios")
    public ResponseEntity<List<IncidentRecord>> getAllScenarios() {
        return ResponseEntity.ok(triageEngine.getAllScenarios());
    }

    @GetMapping("/scenarios/{id}")
    public ResponseEntity<IncidentRecord> getScenarioById(@PathVariable int id) {
        return triageEngine.getScenarioById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
