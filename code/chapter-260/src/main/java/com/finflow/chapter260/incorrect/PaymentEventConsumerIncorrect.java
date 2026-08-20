package com.finflow.chapter260.incorrect;

import com.finflow.chapter260.domain.PaymentEvent;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. No idempotency check -> Duplicate messages cause duplicate billing/settlement!
 * 2. Unhandled poison pills block the consumer partition indefinitely.
 */
@Service
public class PaymentEventConsumerIncorrect {

    private final AtomicInteger doubleBillingCount = new AtomicInteger(0);

    /**
     * Anti-Pattern: Consumes without deduplication check.
     * If Kafka replays an event due to network rebalance, this method processes it again!
     */
    public void consumeWithoutIdempotency(PaymentEvent event) {
        // Double-processing hazard!
        doubleBillingCount.incrementAndGet();
    }

    public int getDoubleBillingCount() {
        return doubleBillingCount.get();
    }

    public void reset() {
        doubleBillingCount.set(0);
    }
}
