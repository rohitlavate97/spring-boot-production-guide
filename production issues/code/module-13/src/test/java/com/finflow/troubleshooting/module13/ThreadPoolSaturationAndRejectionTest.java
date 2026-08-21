package com.finflow.troubleshooting.module13;

import com.finflow.troubleshooting.module13.service.DocumentGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module13Application.class)
public class ThreadPoolSaturationAndRejectionTest {

    @Autowired
    private DocumentGenerationService documentService;

    @Test
    void testCallerRunsPolicyHandlesExcessTasksWithoutDroppingOrCrashing() {
        int totalTasks = 8; // Core: 2, Queue: 2, Max: 4. 8 tasks exceed max+queue, triggering CallerRunsPolicy
        List<CompletableFuture<String>> futures = new ArrayList<>();

        for (int i = 1; i <= totalTasks; i++) {
            futures.add(documentService.generateStatementAsync("STMT-" + i, 50));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        assertThat(futures).hasSize(totalTasks);
        for (CompletableFuture<String> future : futures) {
            assertThat(future.join()).startsWith("GENERATED:STMT-");
        }
    }
}
