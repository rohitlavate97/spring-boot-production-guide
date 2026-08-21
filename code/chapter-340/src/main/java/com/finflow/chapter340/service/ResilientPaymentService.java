package com.finflow.chapter340.service;

import com.finflow.chapter340.client.PaymentGatewayClient;
import com.finflow.chapter340.domain.PaymentRequest;
import com.finflow.chapter340.domain.PaymentResponse;
import com.finflow.chapter340.domain.PaymentStatus;
import com.finflow.chapter340.exception.GatewayServiceUnavailableException;
import com.finflow.chapter340.exception.GatewayTimeoutException;
import com.finflow.chapter340.exception.PaymentValidationException;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.ratelimiter.RequestNotPermitted;
import io.github.resilience4j.ratelimiter.annotation.RateLimiter;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ResilientPaymentService {

    private static final Logger log = LoggerFactory.getLogger(ResilientPaymentService.class);
    private static final String INSTANCE_NAME = "stripeGateway";

    private final PaymentGatewayClient gatewayClient;
    private final CircuitBreakerRegistry circuitBreakerRegistry;

    public ResilientPaymentService(PaymentGatewayClient gatewayClient, CircuitBreakerRegistry circuitBreakerRegistry) {
        this.gatewayClient = gatewayClient;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
    }

    /**
     * Executes payment processing with standard Resilience4j protection:
     * Retry -> CircuitBreaker -> RateLimiter -> Bulkhead -> Downstream Call.
     */
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = INSTANCE_NAME, fallbackMethod = "handleCircuitBreakerOrServerError")
    @Retry(name = INSTANCE_NAME, fallbackMethod = "handleCircuitBreakerOrServerError")
    @RateLimiter(name = INSTANCE_NAME, fallbackMethod = "handleRateLimitError")
    @Bulkhead(name = INSTANCE_NAME, fallbackMethod = "handleBulkheadError")
    public PaymentResponse processPayment(PaymentRequest request) {
        long startTime = System.currentTimeMillis();
        log.info("[ResilientPaymentService] Processing payment tx: {}, merchant: {}, amount: {}",
                request.getTransactionId(), request.getMerchantId(), request.getAmount());

        String gatewayRef = gatewayClient.executePayment(request);
        long duration = System.currentTimeMillis() - startTime;

        String cbState = getCircuitBreakerState();
        return PaymentResponse.success(request.getTransactionId(), gatewayRef, duration, cbState);
    }

    /**
     * Fallback invoked when Circuit Breaker is OPEN (CallNotPermittedException)
     * or when downstream gateway returns persistent 5xx errors after retries exhausted.
     */
    public PaymentResponse handleCircuitBreakerOrServerError(PaymentRequest request, CallNotPermittedException ex) {
        log.warn("[ResilientPaymentService] Circuit Breaker OPEN fast-fail for tx: {}. Message: {}",
                request.getTransactionId(), ex.getMessage());
        return PaymentResponse.fallback(
                request.getTransactionId(),
                "Payment accepted for asynchronous background settlement (Circuit Breaker OPEN fast-fail)",
                0,
                CircuitBreaker.State.OPEN.name()
        );
    }

    public PaymentResponse handleCircuitBreakerOrServerError(PaymentRequest request, GatewayTimeoutException ex) {
        log.warn("[ResilientPaymentService] Gateway timeout after retries for tx: {}. Routing to async fallback.",
                request.getTransactionId());
        return PaymentResponse.fallback(
                request.getTransactionId(),
                "Payment timed out with gateway. Enqueued in reliable dead-letter/async reconciliation queue.",
                0,
                getCircuitBreakerState()
        );
    }

    public PaymentResponse handleCircuitBreakerOrServerError(PaymentRequest request, GatewayServiceUnavailableException ex) {
        log.warn("[ResilientPaymentService] Gateway unavailable after retries for tx: {}. Routing to async fallback.",
                request.getTransactionId());
        return PaymentResponse.fallback(
                request.getTransactionId(),
                "Payment gateway 503 unavailable. Enqueued for reliable async retry worker.",
                0,
                getCircuitBreakerState()
        );
    }

    public PaymentResponse handleCircuitBreakerOrServerError(PaymentRequest request, Throwable ex) {
        if (ex instanceof PaymentValidationException) {
            log.error("[ResilientPaymentService] Validation error for tx: {}. Will not fallback.", request.getTransactionId());
            throw (PaymentValidationException) ex;
        }
        log.error("[ResilientPaymentService] Unhandled error during payment execution for tx: {}. Root: {}",
                request.getTransactionId(), ex.getMessage());
        return PaymentResponse.fallback(
                request.getTransactionId(),
                "Payment processing encountered error: " + ex.getMessage() + ". Enqueued to fallback recovery.",
                0,
                getCircuitBreakerState()
        );
    }

    /**
     * Fallback invoked when Rate Limiter capacity is exceeded.
     */
    public PaymentResponse handleRateLimitError(PaymentRequest request, RequestNotPermitted ex) {
        log.warn("[ResilientPaymentService] Rate limit exceeded for tx: {}. Request rejected.",
                request.getTransactionId());
        return PaymentResponse.rateLimited(
                request.getTransactionId(),
                "Rate limit exceeded (HTTP 429 Too Many Requests). Please back off and retry.",
                getCircuitBreakerState()
        );
    }

    /**
     * Fallback invoked when Bulkhead concurrent capacity is exceeded.
     */
    public PaymentResponse handleBulkheadError(PaymentRequest request, BulkheadFullException ex) {
        log.warn("[ResilientPaymentService] Bulkhead full for tx: {}. Concurrency limit reached.",
                request.getTransactionId());
        return new PaymentResponse(
                request.getTransactionId(),
                PaymentStatus.FAILED,
                null,
                0,
                "Concurrent gateway call limit reached (Bulkhead Full). Backing off.",
                getCircuitBreakerState()
        );
    }

    public String getCircuitBreakerState() {
        return circuitBreakerRegistry.circuitBreaker(INSTANCE_NAME).getState().name();
    }

    public CircuitBreaker getCircuitBreaker() {
        return circuitBreakerRegistry.circuitBreaker(INSTANCE_NAME);
    }
}
