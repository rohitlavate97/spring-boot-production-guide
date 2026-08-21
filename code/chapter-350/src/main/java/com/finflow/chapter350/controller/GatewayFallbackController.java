package com.finflow.chapter350.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

/**
 * Reactive Fallback Controller handling tripped CircuitBreakers at the API Gateway Edge.
 */
@RestController
@RequestMapping("/fallback")
public class GatewayFallbackController {

    private static final Logger log = LoggerFactory.getLogger(GatewayFallbackController.class);

    @RequestMapping("/payments")
    public Mono<ResponseEntity<Map<String, Object>>> paymentServiceFallback(ServerWebExchange exchange) {
        log.warn("[Gateway-Fallback] Payment Service is unavailable. Circuit breaker tripped.");

        Map<String, Object> fallbackBody = Map.of(
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error", "Service Unavailable",
                "message", "Payment service is momentarily degraded. Request routed to edge fallback.",
                "service", "payment-service",
                "circuitBreaker", "paymentCircuitBreaker",
                "timestamp", Instant.now().toString()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(fallbackBody));
    }

    @RequestMapping("/orders")
    public Mono<ResponseEntity<Map<String, Object>>> orderServiceFallback(ServerWebExchange exchange) {
        log.warn("[Gateway-Fallback] Order Service is unavailable. Circuit breaker tripped.");

        Map<String, Object> fallbackBody = Map.of(
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error", "Service Unavailable",
                "message", "Order service is momentarily degraded. Please retry.",
                "service", "order-service",
                "circuitBreaker", "orderCircuitBreaker",
                "timestamp", Instant.now().toString()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(fallbackBody));
    }

    @RequestMapping("/ledger")
    public Mono<ResponseEntity<Map<String, Object>>> ledgerServiceFallback(ServerWebExchange exchange) {
        log.warn("[Gateway-Fallback] Ledger Service is unavailable. Circuit breaker tripped.");

        Map<String, Object> fallbackBody = Map.of(
                "status", HttpStatus.SERVICE_UNAVAILABLE.value(),
                "error", "Service Unavailable",
                "message", "Ledger service is currently offline for maintenance.",
                "service", "ledger-service",
                "circuitBreaker", "ledgerCircuitBreaker",
                "timestamp", Instant.now().toString()
        );

        return Mono.just(ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(fallbackBody));
    }
}
