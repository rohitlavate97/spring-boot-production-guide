package com.finflow.troubleshooting.module13.controller;

import com.finflow.troubleshooting.module13.service.DocumentGenerationService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;

@RestController
@RequestMapping("/api/v1/async")
public class AsyncTelemetryController {

    private final DocumentGenerationService documentService;
    private final ThreadPoolTaskExecutor documentTaskExecutor;

    public AsyncTelemetryController(DocumentGenerationService documentService,
                                    @Qualifier("documentTaskExecutor") ThreadPoolTaskExecutor documentTaskExecutor) {
        this.documentService = documentService;
        this.documentTaskExecutor = documentTaskExecutor;
    }

    @PostMapping("/generate-statement")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> generateStatement(
            @RequestParam String statementId,
            @RequestParam(defaultValue = "100") long durationMs) {

        return documentService.generateStatementAsync(statementId, durationMs)
                .thenApply(result -> ResponseEntity.ok(Map.of(
                        "statementId", statementId,
                        "result", result
                )));
    }

    @GetMapping("/metrics")
    public ResponseEntity<Map<String, Object>> getThreadPoolMetrics() {
        ThreadPoolExecutor threadPool = documentTaskExecutor.getThreadPoolExecutor();
        return ResponseEntity.ok(Map.of(
                "activeCount", threadPool.getActiveCount(),
                "poolSize", threadPool.getPoolSize(),
                "corePoolSize", threadPool.getCorePoolSize(),
                "maximumPoolSize", threadPool.getMaximumPoolSize(),
                "queueSize", threadPool.getQueue().size(),
                "remainingQueueCapacity", threadPool.getQueue().remainingCapacity(),
                "completedTaskCount", threadPool.getCompletedTaskCount(),
                "taskCount", threadPool.getTaskCount()
        ));
    }
}
