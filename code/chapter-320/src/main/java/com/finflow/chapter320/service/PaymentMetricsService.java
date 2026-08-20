package com.finflow.chapter320.service;

import com.finflow.chapter320.domain.PaymentTransaction;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class PaymentMetricsService {

    private final MeterRegistry registry;
    private final AtomicInteger inFlightRequests = new AtomicInteger(0);
    private final DistributionSummary amountSummary;

    public PaymentMetricsService(MeterRegistry registry) {
        this.registry = registry;

        // Register Gauge: Tracks current in-flight concurrent payment requests
        registry.gauge("payment.inflight.requests", inFlightRequests);

        // Register DistributionSummary: Measures distribution of payment amounts ($1 to $10,000)
        this.amountSummary = DistributionSummary.builder("payment.transaction.amount")
                .description("Distribution of payment amounts processed")
                .baseUnit("USD")
                .maximumExpectedValue(100_000.0)
                .register(registry);
    }

    public PaymentTransaction processPayment(PaymentTransaction transaction) {
        inFlightRequests.incrementAndGet();
        long startNanos = System.nanoTime();

        try {
            // Simulate payment processing work
            if (transaction.getLatencyMs() > 0) {
                Thread.sleep(transaction.getLatencyMs());
            }

            // Record Amount Distribution
            if (transaction.getAmount() != null) {
                amountSummary.record(transaction.getAmount().doubleValue());
            }

            // Record Counter: Monotonically increasing total transaction count with bounded tags
            Counter.builder("payment.transactions.total")
                    .tag("currency", transaction.getCurrency() != null ? transaction.getCurrency() : "USD")
                    .tag("payment_method", transaction.getPaymentMethod() != null ? transaction.getPaymentMethod() : "CREDIT_CARD")
                    .tag("status", transaction.getStatus() != null ? transaction.getStatus() : "SUCCESS")
                    .register(registry)
                    .increment();

            return transaction;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            transaction.setStatus("FAILED");
            return transaction;
        } finally {
            inFlightRequests.decrementAndGet();
            long durationNanos = System.nanoTime() - startNanos;

            // Record Timer: Latency distribution
            Timer.builder("payment.processing.duration")
                    .tag("payment_method", transaction.getPaymentMethod() != null ? transaction.getPaymentMethod() : "CREDIT_CARD")
                    .tag("status", transaction.getStatus() != null ? transaction.getStatus() : "SUCCESS")
                    .register(registry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * ANTI-PATTERN: High-Cardinality Tagging.
     * Adding unbounded dynamic strings (transactionId, cardNumber) creates a new time series
     * per transaction, resulting in Prometheus OutOfMemory crashes.
     */
    public void recordWithHighCardinality(String transactionId, String cardNumber) {
        Counter.builder("payment.unbounded.transactions")
                .tag("transaction_id", transactionId) // DANGEROUS: Millions of unique IDs
                .tag("card_number", cardNumber)       // DANGEROUS: High cardinality & PCI violation!
                .register(registry)
                .increment();
    }

    public int getInFlightRequests() {
        return inFlightRequests.get();
    }
}
