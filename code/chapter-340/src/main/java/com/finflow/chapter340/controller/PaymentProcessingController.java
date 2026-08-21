package com.finflow.chapter340.controller;

import com.finflow.chapter340.client.PaymentGatewayClient;
import com.finflow.chapter340.domain.PaymentRequest;
import com.finflow.chapter340.domain.PaymentResponse;
import com.finflow.chapter340.listener.CircuitBreakerEventTracker;
import com.finflow.chapter340.service.ResilientPaymentService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentProcessingController {

    private final ResilientPaymentService paymentService;
    private final PaymentGatewayClient gatewayClient;
    private final CircuitBreakerEventTracker eventTracker;

    public PaymentProcessingController(ResilientPaymentService paymentService,
                                       PaymentGatewayClient gatewayClient,
                                       CircuitBreakerEventTracker eventTracker) {
        this.paymentService = paymentService;
        this.gatewayClient = gatewayClient;
        this.eventTracker = eventTracker;
    }

    @PostMapping("/process")
    public ResponseEntity<PaymentResponse> processPayment(@RequestBody PaymentRequest request) {
        PaymentResponse response = paymentService.processPayment(request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/circuit-breaker/state")
    public ResponseEntity<Map<String, Object>> getCircuitBreakerState() {
        CircuitBreaker cb = paymentService.getCircuitBreaker();
        CircuitBreaker.Metrics metrics = cb.getMetrics();

        Map<String, Object> details = new HashMap<>();
        details.put("name", cb.getName());
        details.put("state", cb.getState().name());
        details.put("failureRate", metrics.getFailureRate());
        details.put("slowCallRate", metrics.getSlowCallRate());
        details.put("numberOfSuccessfulCalls", metrics.getNumberOfSuccessfulCalls());
        details.put("numberOfFailedCalls", metrics.getNumberOfFailedCalls());
        details.put("numberOfSlowCalls", metrics.getNumberOfSlowCalls());
        details.put("numberOfNotPermittedCalls", metrics.getNumberOfNotPermittedCalls());
        details.put("numberOfBufferedCalls", metrics.getNumberOfBufferedCalls());
        details.put("gatewayOutageActive", gatewayClient.isForcedOutage());

        return ResponseEntity.ok(details);
    }

    @PostMapping("/simulate-outage")
    public ResponseEntity<Map<String, Object>> setSimulateOutage(@RequestParam boolean enabled) {
        gatewayClient.setForcedOutage(enabled);
        Map<String, Object> response = new HashMap<>();
        response.put("simulatedOutage", enabled);
        response.put("circuitBreakerState", paymentService.getCircuitBreakerState());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/circuit-breaker/events")
    public ResponseEntity<List<String>> getEvents() {
        return ResponseEntity.ok(eventTracker.getEventLogs());
    }

    @PostMapping("/circuit-breaker/reset")
    public ResponseEntity<Map<String, String>> resetCircuitBreaker() {
        paymentService.getCircuitBreaker().reset();
        eventTracker.clearEventLogs();
        gatewayClient.resetCallCount();
        return ResponseEntity.ok(Map.of("status", "RESET", "state", paymentService.getCircuitBreakerState()));
    }
}
