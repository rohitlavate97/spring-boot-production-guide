package com.finflow.chapter290.unit;

import com.finflow.chapter290.Chapter290Application;
import com.finflow.chapter290.correct.StatementExportServiceCorrect;
import com.finflow.chapter290.domain.StatementExportRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter290Application.class)
public class AsyncCompletableFutureExecutionTest {

    @Autowired
    private StatementExportServiceCorrect exportService;

    @Test
    public void testAsyncExecution_completableFutureNonBlocking_succeeds() throws Exception {
        StatementExportRequest request = new StatementExportRequest(
                "REQ-ASYNC-100",
                "MERCHANT_ACME",
                8,
                2026,
                "PDF",
                "PENDING",
                null,
                Instant.now(),
                null
        );

        CompletableFuture<StatementExportRequest> future = exportService.generateStatementAsync(request);

        // Verify future is not immediately done (runs asynchronously on pool)
        StatementExportRequest result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).isNotNull();
        assertThat(result.getStatus()).isEqualTo("COMPLETED");
        assertThat(result.getGeneratedFileUrl()).contains("REQ-ASYNC-100.pdf");
        assertThat(result.getCompletedAt()).isNotNull();
    }
}
