package com.finflow.troubleshooting.module01.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class OrderProcessingService {

    private static final Logger log = LoggerFactory.getLogger(OrderProcessingService.class);

    private final PaymentSettlementService paymentSettlementService;
    private final Map<String, String> orderStore = new ConcurrentHashMap<>();

    public OrderProcessingService(PaymentSettlementService paymentSettlementService) {
        this.paymentSettlementService = paymentSettlementService;
    }

    public Map<String, Object> processOrder(String customerId, BigDecimal amount) {
        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[OrderProcessing] Creating order '{}' for customer '{}' with amount ${}", orderId, customerId, amount);

        String paymentId = paymentSettlementService.settlePayment(orderId, customerId, amount);
        orderStore.put(orderId, paymentId);

        return Map.of(
                "orderId", orderId,
                "customerId", customerId,
                "paymentId", paymentId,
                "status", "CONFIRMED"
        );
    }
}
