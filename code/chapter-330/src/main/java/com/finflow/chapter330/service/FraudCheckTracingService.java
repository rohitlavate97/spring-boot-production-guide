package com.finflow.chapter330.service;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FraudCheckTracingService {

    private static final Logger log = LoggerFactory.getLogger(FraudCheckTracingService.class);
    private final Tracer tracer;

    public FraudCheckTracingService(Tracer tracer) {
        this.tracer = tracer;
    }

    public String evaluateFraudRisk(String merchantId, double amount) {
        // Create custom child span for fraud analysis
        Span childSpan = tracer.nextSpan().name("fraud-evaluation-engine").start();

        try (Tracer.SpanInScope spanInScope = tracer.withSpan(childSpan)) {
            childSpan.tag("fraud.risk.threshold", "85");
            childSpan.tag("merchant.id", merchantId);
            childSpan.tag("transaction.amount", String.valueOf(amount));
            childSpan.event("rule.evaluation.started");

            log.info("Executing rule-based fraud check for merchant: {} | Amount: {}", merchantId, amount);

            // Simulate computation latency
            Thread.sleep(15);

            String decision = amount > 10000.0 ? "MANUAL_REVIEW" : "APPROVED";
            childSpan.tag("fraud.decision", decision);
            childSpan.event("rule.evaluation.completed");

            return decision;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            childSpan.error(e);
            return "REJECTED";
        } finally {
            childSpan.end();
        }
    }
}
