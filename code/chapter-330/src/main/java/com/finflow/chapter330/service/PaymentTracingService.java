package com.finflow.chapter330.service;

import com.finflow.chapter330.domain.PaymentTraceRequest;
import com.finflow.chapter330.domain.TraceDiagnostics;
import io.micrometer.tracing.Baggage;
import io.micrometer.tracing.BaggageInScope;
import io.micrometer.tracing.BaggageManager;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PaymentTracingService {

    private static final Logger log = LoggerFactory.getLogger(PaymentTracingService.class);

    private final Tracer tracer;
    private final BaggageManager baggageManager;
    private final FraudCheckTracingService fraudCheckService;

    public PaymentTracingService(Tracer tracer,
                                 BaggageManager baggageManager,
                                 FraudCheckTracingService fraudCheckService) {
        this.tracer = tracer;
        this.baggageManager = baggageManager;
        this.fraudCheckService = fraudCheckService;
    }

    public TraceDiagnostics processPayment(PaymentTraceRequest request) {
        long startTime = System.currentTimeMillis();

        Span currentSpan = tracer.currentSpan();
        String traceId = currentSpan != null ? currentSpan.context().traceId() : "NO_TRACE";
        String spanId = currentSpan != null ? currentSpan.context().spanId() : "NO_SPAN";

        // Keep Baggage active throughout the entire downstream transaction execution
        BaggageInScope baggageScope = null;
        if (request.getMerchantId() != null) {
            baggageScope = baggageManager.createBaggageInScope("merchant-id", request.getMerchantId());
        }

        try {
            if (baggageScope != null) {
                log.info("Processing checkout payment: {} | Merchant Baggage: {}",
                        request.getPaymentId(), baggageScope.get());
            }

            // Delegate to child traced service
            double amountVal = request.getAmount() != null ? request.getAmount().doubleValue() : 0.0;
            String fraudDecision = fraudCheckService.evaluateFraudRisk(request.getMerchantId(), amountVal);

            long duration = System.currentTimeMillis() - startTime;

            Baggage merchantBaggage = baggageManager.getBaggage("merchant-id");
            String baggageVal = merchantBaggage != null ? merchantBaggage.get() : request.getMerchantId();

            return new TraceDiagnostics(traceId, spanId, baggageVal, fraudDecision, duration);
        } finally {
            if (baggageScope != null) {
                baggageScope.close();
            }
        }
    }
}
