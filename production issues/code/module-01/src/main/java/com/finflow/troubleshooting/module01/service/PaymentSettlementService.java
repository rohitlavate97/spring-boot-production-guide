package com.finflow.troubleshooting.module01.service;

import com.finflow.troubleshooting.module01.event.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentSettlementService {

    private static final Logger log = LoggerFactory.getLogger(PaymentSettlementService.class);

    private final ApplicationEventPublisher eventPublisher;

    public PaymentSettlementService(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    public String settlePayment(String orderId, String customerId, BigDecimal amount) {
        String paymentId = "PAY-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.info("[PaymentSettlement] Successfully charged ${} for Order '{}', Payment ID: {}",
                amount, orderId, paymentId);

        // Decoupled Event Publication: Does NOT require direct circular dependency on Notification/Order service
        eventPublisher.publishEvent(new PaymentCompletedEvent(orderId, customerId, amount, Instant.now()));

        return paymentId;
    }
}
