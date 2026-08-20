package com.finflow.chapter290.unit;

import com.finflow.chapter290.Chapter290Application;
import com.finflow.chapter290.correct.StatementExportServiceCorrect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter290Application.class)
public class Java21VirtualThreadExecutionTest {

    @Autowired
    private StatementExportServiceCorrect exportService;

    @Test
    public void testVirtualThreadExecution_executesOnJava21VirtualThread() throws Exception {
        CompletableFuture<Boolean> future = exportService.fetchReportOnVirtualThread();

        Boolean isVirtual = future.get(5, TimeUnit.SECONDS);

        // Verifies the method ran on a Java 21 Project Loom virtual thread!
        assertThat(isVirtual).isTrue();
    }
}
