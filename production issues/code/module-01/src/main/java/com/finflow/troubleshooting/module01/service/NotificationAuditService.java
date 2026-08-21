package com.finflow.troubleshooting.module01.service;

import com.finflow.troubleshooting.module01.event.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

@Service
public class NotificationAuditService {

    private static final Logger log = LoggerFactory.getLogger(NotificationAuditService.class);
    private final AtomicInteger processedNotifications = new AtomicInteger(0);

    @EventListener
    public void onPaymentCompleted(PaymentCompletedEvent event) {
        processedNotifications.incrementAndGet();
        log.info("[NotificationAudit] Sent payment confirmation notification for Order: {}, Customer: {}, Amount: ${}",
                event.orderId(), event.customerId(), event.amount());
    }

    public int getProcessedNotificationsCount() {
        return processedNotifications.get();
    }
}
