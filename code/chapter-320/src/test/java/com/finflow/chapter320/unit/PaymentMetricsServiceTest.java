package com.finflow.chapter320.unit;

import com.finflow.chapter320.domain.PaymentTransaction;
import com.finflow.chapter320.service.PaymentMetricsService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class PaymentMetricsServiceTest {

    private MeterRegistry registry;
    private PaymentMetricsService metricsService;

    @BeforeEach
    public void setup() {
        registry = new SimpleMeterRegistry();
        metricsService = new PaymentMetricsService(registry);
    }

    @Test
    public void testProcessPayment_recordsCounterTimerAndSummary() {
        // Execute 3 USD SUCCESS payments
        for (int i = 0; i < 3; i++) {
            PaymentTransaction tx = new PaymentTransaction(
                    "TX-" + i, "MERCH-1", "USD", BigDecimal.valueOf(100.0), "CREDIT_CARD", "SUCCESS", 10
            );
            metricsService.processPayment(tx);
        }

        // Execute 1 EUR SUCCESS payment
        PaymentTransaction txEur = new PaymentTransaction(
                "TX-EUR", "MERCH-2", "EUR", BigDecimal.valueOf(250.0), "ACH", "SUCCESS", 15
        );
        metricsService.processPayment(txEur);

        // Execute 1 USD FAILED payment
        PaymentTransaction txFail = new PaymentTransaction(
                "TX-FAIL", "MERCH-1", "USD", BigDecimal.valueOf(50.0), "CREDIT_CARD", "FAILED", 5
        );
        metricsService.processPayment(txFail);

        // Verify Counter increments
        Counter usdSuccessCounter = registry.find("payment.transactions.total")
                .tag("currency", "USD")
                .tag("status", "SUCCESS")
                .counter();
        assertThat(usdSuccessCounter).isNotNull();
        assertThat(usdSuccessCounter.count()).isEqualTo(3.0);

        Counter eurSuccessCounter = registry.find("payment.transactions.total")
                .tag("currency", "EUR")
                .tag("status", "SUCCESS")
                .counter();
        assertThat(eurSuccessCounter).isNotNull();
        assertThat(eurSuccessCounter.count()).isEqualTo(1.0);

        // Verify Timer recordings
        Timer creditCardTimer = registry.find("payment.processing.duration")
                .tag("payment_method", "CREDIT_CARD")
                .tag("status", "SUCCESS")
                .timer();
        assertThat(creditCardTimer).isNotNull();
        assertThat(creditCardTimer.count()).isEqualTo(3L);
        assertThat(creditCardTimer.totalTime(TimeUnit.MILLISECONDS)).isGreaterThan(0.0);

        // Verify DistributionSummary
        DistributionSummary summary = registry.find("payment.transaction.amount").summary();
        assertThat(summary).isNotNull();
        assertThat(summary.count()).isEqualTo(5L);
        assertThat(summary.totalAmount()).isEqualTo(600.0); // 3*100 + 250 + 50 = 600
    }
}
