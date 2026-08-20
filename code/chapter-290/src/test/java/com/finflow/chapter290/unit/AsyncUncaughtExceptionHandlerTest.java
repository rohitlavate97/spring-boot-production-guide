package com.finflow.chapter290.unit;

import com.finflow.chapter290.Chapter290Application;
import com.finflow.chapter290.config.AsyncConfig;
import com.finflow.chapter290.correct.StatementExportServiceCorrect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(classes = Chapter290Application.class)
public class AsyncUncaughtExceptionHandlerTest {

    @Autowired
    private StatementExportServiceCorrect exportService;

    @Autowired
    private AsyncConfig asyncConfig;

    @BeforeEach
    public void setup() {
        asyncConfig.resetUncaughtExceptionCount();
    }

    @Test
    public void testAsyncVoidMethodException_handledByCustomUncaughtExceptionHandler() {
        // Fire-and-forget method with exception
        exportService.fireAndForgetNotification("MERCHANT_FAIL_100", true);

        // Verify the exception is captured by the custom AsyncUncaughtExceptionHandler
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(asyncConfig.getUncaughtExceptionCount()).isEqualTo(1);
        });
    }
}
