package com.finflow.troubleshooting.module13.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class DocumentGenerationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentGenerationService.class);

    @Async("documentTaskExecutor")
    public CompletableFuture<String> generateStatementAsync(String statementId, long durationMs) {
        String threadName = Thread.currentThread().getName();
        log.info("[AsyncService] Processing statement {} on thread: {}", statementId, threadName);

        if (durationMs > 0) {
            try {
                Thread.sleep(durationMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("[AsyncService] Task interrupted for statement: {}", statementId);
                return CompletableFuture.completedFuture("INTERRUPTED");
            }
        }

        return CompletableFuture.completedFuture("GENERATED:" + statementId + ":BY:" + threadName);
    }
}
