package com.finflow.troubleshooting.module15.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class OrderProcessingService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingService.class);

    @Async("observabilityTaskExecutor")
    public CompletableFuture<String> processOrderNotificationAsync(String orderId) {
        String currentCorrelationId = MDC.get("correlationId");
        String threadName = Thread.currentThread().getName();

        log.info("[AsyncOrderService] Processing order {} on thread {} with correlationId={}",
                orderId, threadName, currentCorrelationId);

        return CompletableFuture.completedFuture("PROCESSED:" + orderId + ":CORR:" + currentCorrelationId);
    }
}
