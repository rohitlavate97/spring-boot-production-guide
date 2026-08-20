package com.finflow.chapter290.correct;

import com.finflow.chapter290.domain.StatementExportRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Service
public class StatementExportServiceCorrect {

    private static final Logger log = LoggerFactory.getLogger(StatementExportServiceCorrect.class);

    @Async("statementExportExecutor")
    public CompletableFuture<StatementExportRequest> generateStatementAsync(StatementExportRequest request) {
        log.info("Processing export on platform thread: {}", Thread.currentThread().getName());

        try {
            Thread.sleep(50); // Simulate rendering PDF
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        request.setStatus("COMPLETED");
        request.setGeneratedFileUrl("https://s3.finflow.io/statements/" + request.getRequestId() + ".pdf");
        request.setCompletedAt(Instant.now());

        return CompletableFuture.completedFuture(request);
    }

    @Async("virtualThreadExecutor")
    public CompletableFuture<Boolean> fetchReportOnVirtualThread() {
        boolean isVirtual = Thread.currentThread().isVirtual();
        log.info("Executing on Java 21 Virtual Thread? isVirtual={}, thread={}", isVirtual, Thread.currentThread());
        return CompletableFuture.completedFuture(isVirtual);
    }

    @Async
    public void fireAndForgetNotification(String merchantId, boolean fail) {
        log.info("Sending fire-and-forget notification to merchant: {}", merchantId);
        if (fail) {
            throw new IllegalStateException("Simulated failure in fire-and-forget async method for merchant: " + merchantId);
        }
    }
}
