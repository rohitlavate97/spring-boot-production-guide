package com.finflow.troubleshooting.module01.controller;

import com.finflow.troubleshooting.module01.service.NotificationAuditService;
import com.finflow.troubleshooting.module01.service.OrderProcessingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderProcessingService orderProcessingService;
    private final NotificationAuditService notificationAuditService;

    public OrderController(OrderProcessingService orderProcessingService,
                           NotificationAuditService notificationAuditService) {
        this.orderProcessingService = orderProcessingService;
        this.notificationAuditService = notificationAuditService;
    }

    @PostMapping("/checkout")
    public ResponseEntity<Map<String, Object>> checkout(
            @RequestParam(defaultValue = "CUST-901") String customerId,
            @RequestParam(defaultValue = "150.00") BigDecimal amount) {
        Map<String, Object> result = orderProcessingService.processOrder(customerId, amount);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/notifications/count")
    public ResponseEntity<Map<String, Object>> getNotificationCount() {
        return ResponseEntity.ok(Map.of(
                "notificationCount", notificationAuditService.getProcessedNotificationsCount()
        ));
    }
}
