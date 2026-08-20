package com.finflow.chapter300.controller;

import com.finflow.chapter300.domain.ContainerRuntimeDiagnostics;
import com.finflow.chapter300.domain.MemoryLimitCalculation;
import com.finflow.chapter300.service.ContainerDiagnosticsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/container")
public class ContainerDiagnosticsController {

    private final ContainerDiagnosticsService diagnosticsService;

    public ContainerDiagnosticsController(ContainerDiagnosticsService diagnosticsService) {
        this.diagnosticsService = diagnosticsService;
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<ContainerRuntimeDiagnostics> getDiagnostics() {
        return ResponseEntity.ok(diagnosticsService.getDiagnostics());
    }

    @PostMapping("/memory-layout-calculator")
    public ResponseEntity<MemoryLimitCalculation> calculateMemoryLayout(
            @RequestParam(defaultValue = "2048") long containerLimitMb,
            @RequestParam(defaultValue = "75.0") double maxRamPercentage) {
        return ResponseEntity.ok(diagnosticsService.calculateMemoryLayout(containerLimitMb, maxRamPercentage));
    }
}
