package com.finflow.troubleshooting.module16.controller;

import com.finflow.troubleshooting.module16.service.CgroupDiagnosticsService;
import com.finflow.troubleshooting.module16.service.ContainerMemoryCalculator;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/container")
public class ContainerDiagnosticsController {

    private final CgroupDiagnosticsService cgroupService;
    private final ContainerMemoryCalculator memoryCalculator;

    public ContainerDiagnosticsController(CgroupDiagnosticsService cgroupService,
                                          ContainerMemoryCalculator memoryCalculator) {
        this.cgroupService = cgroupService;
        this.memoryCalculator = memoryCalculator;
    }

    @GetMapping("/diagnostics")
    public ResponseEntity<Map<String, Object>> getContainerDiagnostics() {
        return ResponseEntity.ok(cgroupService.getCgroupAndJvmDiagnostics());
    }

    @GetMapping("/memory-budget")
    public ResponseEntity<ContainerMemoryCalculator.MemoryBudgetResult> getMemoryBudget(
            @RequestParam(defaultValue = "1024") long containerMemoryMb,
            @RequestParam(defaultValue = "75.0") double maxRamPercentage,
            @RequestParam(defaultValue = "200") int threadCount,
            @RequestParam(defaultValue = "256") long metaspaceMb,
            @RequestParam(defaultValue = "128") long directMemoryMb
    ) {
        var budget = memoryCalculator.calculateBudget(
                containerMemoryMb,
                maxRamPercentage,
                threadCount,
                metaspaceMb,
                directMemoryMb
        );
        return ResponseEntity.ok(budget);
    }

    @PostMapping("/simulate-offheap")
    public ResponseEntity<Map<String, Object>> simulateOffHeapAllocation(@RequestParam(defaultValue = "50") int mb) {
        cgroupService.allocateDirectMemoryMb(mb);
        return ResponseEntity.ok(Map.of(
                "status", "OFF_HEAP_ALLOCATED",
                "allocatedMb", mb,
                "totalSimulatedOffHeapMb", cgroupService.getSimulatedDirectMemoryMb()
        ));
    }

    @PostMapping("/clear-offheap")
    public ResponseEntity<Map<String, Object>> clearOffHeap() {
        cgroupService.clearDirectMemory();
        return ResponseEntity.ok(Map.of(
                "status", "CLEARED",
                "totalSimulatedOffHeapMb", cgroupService.getSimulatedDirectMemoryMb()
        ));
    }

    @PostMapping("/simulate-cpu-work")
    public ResponseEntity<Map<String, Object>> simulateCpuWork(@RequestParam(defaultValue = "5000000") long iterations) {
        long start = System.nanoTime();
        double sum = 0.0;
        for (long i = 1; i <= iterations; i++) {
            sum += Math.sqrt(i) * Math.sin(i);
        }
        long durationMs = (System.nanoTime() - start) / 1_000_000;
        return ResponseEntity.ok(Map.of(
                "status", "COMPLETED",
                "iterations", iterations,
                "computedSum", sum,
                "durationMs", durationMs
        ));
    }
}
