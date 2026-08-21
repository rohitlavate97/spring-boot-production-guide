package com.finflow.troubleshooting.module13;

import com.finflow.troubleshooting.module13.service.DocumentGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module13Application.class)
public class AsyncExecutionCompletionTest {

    @Autowired
    private DocumentGenerationService documentService;

    @Test
    void testAsyncDocumentGenerationExecutesOnWorkerThread() throws Exception {
        CompletableFuture<String> future = documentService.generateStatementAsync("STMT-999", 20);
        String result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).contains("GENERATED:STMT-999");
        assertThat(result).contains("doc-worker-");
    }
}
