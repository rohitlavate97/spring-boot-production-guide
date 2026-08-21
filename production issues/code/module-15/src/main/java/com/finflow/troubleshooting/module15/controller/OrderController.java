package com.finflow.troubleshooting.module15.controller;

import com.finflow.troubleshooting.module15.service.OrderProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderProcessingService orderProcessingService;

    public OrderController(OrderProcessingService orderProcessingService) {
        this.orderProcessingService = orderProcessingService;
    }

    @PostMapping("/submit")
    public CompletableFuture<ResponseEntity<Map<String, Object>>> submitOrder(@RequestParam String orderId) {
        String correlationId = MDC.get("correlationId");
        log.info("[OrderController] Received order {} with correlationId={}", orderId, correlationId);

        return orderProcessingService.processOrderNotificationAsync(orderId)
                .thenApply(asyncResult -> ResponseEntity.ok(Map.of(
                        "orderId", orderId,
                        "correlationId", correlationId != null ? correlationId : "NONE",
                        "asyncResult", asyncResult
                )));
    }
}
