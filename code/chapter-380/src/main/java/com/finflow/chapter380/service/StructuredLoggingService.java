package com.finflow.chapter380.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Service emitting high-context structured logs correlated with OpenTelemetry MDC headers
 * and recording Prometheus SLO Golden Signals (Latency, Errors, Traffic).
 */
@Service
public class StructuredLoggingService {

    private static final Logger log = LoggerFactory.getLogger(StructuredLoggingService.class);

    private final Counter paymentProcessedCounter;
    private final Counter paymentErrorCounter;
    private final Timer paymentProcessingTimer;

    public StructuredLoggingService(MeterRegistry meterRegistry) {
        this.paymentProcessedCounter = Counter.builder("finflow.payments.processed.total")
                .description("Total number of processed payment authorizations")
                .tag("service", "payment-service")
                .register(meterRegistry);

        this.paymentErrorCounter = Counter.builder("finflow.payments.errors.total")
                .description("Total number of failed payment authorizations")
                .tag("service", "payment-service")
                .register(meterRegistry);

        this.paymentProcessingTimer = Timer.builder("finflow.payments.duration.seconds")
                .description("Latency distribution of payment processing")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);
    }

    public void logPaymentTransaction(String orderId, String merchantId, BigDecimal amount,
                                      String rawPan, boolean success, long durationMs) {
        String traceId = MDC.get("trace_id");
        if (traceId == null) {
            traceId = "tr-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            MDC.put("trace_id", traceId);
        }

        try {
            paymentProcessingTimer.record(durationMs, TimeUnit.MILLISECONDS);

            if (success) {
                paymentProcessedCounter.increment();
                // Notice the raw PAN is passed into the log - PiiMaskingConverter will intercept and mask it!
                log.info("[StructuredPaymentLog] OrderId: {} | MerchantId: {} | Amount: ${} | Card: {} | Status: SUCCESS | Duration: {}ms",
                        orderId, merchantId, amount, rawPan, durationMs);
            } else {
                paymentErrorCounter.increment();
                log.error("[StructuredPaymentLog] OrderId: {} | MerchantId: {} | Amount: ${} | Card: {} | Status: FAILED | Duration: {}ms",
                        orderId, merchantId, amount, rawPan, durationMs);
            }
        } finally {
            MDC.remove("trace_id");
        }
    }
}
