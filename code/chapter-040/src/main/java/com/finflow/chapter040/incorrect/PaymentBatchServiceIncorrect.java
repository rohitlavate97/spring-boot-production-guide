package com.finflow.chapter040.incorrect;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class PaymentBatchServiceIncorrect {
    private static final Logger log = LoggerFactory.getLogger(PaymentBatchServiceIncorrect.class);

    private final List<String> inFlightPayments = new CopyOnWriteArrayList<>();

    public void addInFlight(String paymentId) {
        inFlightPayments.add(paymentId);
    }

    // BUG: Unbounded graceful shutdown time. Exceeds K8s 30s limit if >300 entries.
    @PreDestroy
    public void flushInFlight() {
        log.info("Flushing {} in-flight payments to persistent store...", inFlightPayments.size());
        for (String paymentId : inFlightPayments) {
            try {
                // Simulate slow 100ms Redis write per payment
                Thread.sleep(100);
                log.debug("Flushed {}", paymentId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.error("Flush interrupted!");
                break;
            }
        }
        log.info("Finished flushing in-flight payments");
    }
}
