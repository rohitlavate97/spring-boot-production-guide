package com.finflow.troubleshooting.module15;

import com.finflow.troubleshooting.module15.service.OrderProcessingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module15Application.class)
public class MdcPropagationAsyncTest {

    @Autowired
    private OrderProcessingService orderProcessingService;

    @AfterEach
    void tearDown() {
        MDC.clear();
    }

    @Test
    void testMdcCorrelationIdPropagatesToAsyncWorkerThreadViaTaskDecorator() throws Exception {
        String expectedCorrId = "ASYNC-CORR-5555";
        MDC.put("correlationId", expectedCorrId);

        CompletableFuture<String> future = orderProcessingService.processOrderNotificationAsync("ORD-ASYNC-1");
        String result = future.get(5, TimeUnit.SECONDS);

        assertThat(result).contains("ORD-ASYNC-1");
        assertThat(result).contains("CORR:" + expectedCorrId);
    }
}
