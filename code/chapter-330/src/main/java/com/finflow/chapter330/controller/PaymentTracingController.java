package com.finflow.chapter330.controller;

import com.finflow.chapter330.domain.PaymentTraceRequest;
import com.finflow.chapter330.domain.TraceDiagnostics;
import com.finflow.chapter330.service.PaymentTracingService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments/trace")
public class PaymentTracingController {

    private final PaymentTracingService tracingService;
    private final Tracer tracer;

    public PaymentTracingController(PaymentTracingService tracingService, Tracer tracer) {
        this.tracingService = tracingService;
        this.tracer = tracer;
    }

    @PostMapping("/checkout")
    public ResponseEntity<TraceDiagnostics> processCheckout(@RequestBody PaymentTraceRequest request) {
        if (request.getPaymentId() == null) {
            request.setPaymentId("PAY-" + UUID.randomUUID().toString().substring(0, 8));
        }
        TraceDiagnostics diagnostics = tracingService.processPayment(request);
        return ResponseEntity.ok(diagnostics);
    }

    @GetMapping("/current")
    public ResponseEntity<Map<String, String>> getCurrentTraceContext() {
        Span currentSpan = tracer.currentSpan();
        String traceId = currentSpan != null ? currentSpan.context().traceId() : "NONE";
        String spanId = currentSpan != null ? currentSpan.context().spanId() : "NONE";

        return ResponseEntity.ok(Map.of(
                "traceId", traceId,
                "spanId", spanId
        ));
    }
}
