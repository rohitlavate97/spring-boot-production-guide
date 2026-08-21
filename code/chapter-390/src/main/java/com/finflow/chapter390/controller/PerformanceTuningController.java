package com.finflow.chapter390.controller;

import com.finflow.chapter390.model.GcInfoSnapshot;
import com.finflow.chapter390.model.PerformanceBenchmarkReport;
import com.finflow.chapter390.service.GcInfoCollectorService;
import com.finflow.chapter390.service.JfrProfilingService;
import com.finflow.chapter390.service.OptimizedFeeCalculatorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/performance")
public class PerformanceTuningController {

    private final OptimizedFeeCalculatorService feeCalculatorService;
    private final GcInfoCollectorService gcInfoCollectorService;
    private final JfrProfilingService jfrProfilingService;

    public PerformanceTuningController(OptimizedFeeCalculatorService feeCalculatorService,
                                       GcInfoCollectorService gcInfoCollectorService,
                                       JfrProfilingService jfrProfilingService) {
        this.feeCalculatorService = feeCalculatorService;
        this.gcInfoCollectorService = gcInfoCollectorService;
        this.jfrProfilingService = jfrProfilingService;
    }

    @GetMapping("/benchmark")
    public ResponseEntity<PerformanceBenchmarkReport> runBenchmark(
            @RequestParam(defaultValue = "10000") int iterations,
            @RequestParam(defaultValue = "8") int concurrency) throws InterruptedException {
        PerformanceBenchmarkReport report = feeCalculatorService.runBenchmark(iterations, concurrency);
        return ResponseEntity.ok(report);
    }

    @GetMapping("/gc-metrics")
    public ResponseEntity<GcInfoSnapshot> getGcMetrics() {
        GcInfoSnapshot snapshot = gcInfoCollectorService.collectGcInfo();
        return ResponseEntity.ok(snapshot);
    }

    @PostMapping("/jfr/start")
    public ResponseEntity<Map<String, String>> startJfr(
            @RequestParam(defaultValue = "production-profiling") String name,
            @RequestParam(defaultValue = "10") int maxAgeMinutes) {
        String result = jfrProfilingService.startRecording(name, maxAgeMinutes);
        return ResponseEntity.ok(Map.of("status", result, "recordingName", name));
    }

    @PostMapping("/jfr/stop")
    public ResponseEntity<Map<String, String>> stopJfr(
            @RequestParam(required = false) String dumpPath) {
        Path destination = dumpPath != null ? Path.of(dumpPath) : null;
        String result = jfrProfilingService.stopAndDumpRecording(destination);
        return ResponseEntity.ok(Map.of("status", result));
    }

    @GetMapping("/jfr/status")
    public ResponseEntity<Map<String, Object>> getJfrStatus() {
        return ResponseEntity.ok(jfrProfilingService.getRecordingStatus());
    }
}
