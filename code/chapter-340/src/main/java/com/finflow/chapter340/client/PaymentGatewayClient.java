package com.finflow.chapter340.client;

import com.finflow.chapter340.domain.PaymentRequest;
import com.finflow.chapter340.exception.GatewayServiceUnavailableException;
import com.finflow.chapter340.exception.GatewayTimeoutException;
import com.finflow.chapter340.exception.PaymentValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates an external third-party payment gateway (e.g. Stripe, Adyen).
 */
@Component
public class PaymentGatewayClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentGatewayClient.class);

    private final AtomicBoolean forcedOutage = new AtomicBoolean(false);
    private final AtomicInteger callCount = new AtomicInteger(0);

    public String executePayment(PaymentRequest request) {
        int attempt = callCount.incrementAndGet();
        log.info("[PaymentGatewayClient] Invoking third-party gateway for tx: {} (Attempt: {})",
                request.getTransactionId(), attempt);

        // Validation check - unretryable client error
        if (request.getAmount() == null || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new PaymentValidationException("Invalid payment amount: " + request.getAmount());
        }

        if (forcedOutage.get() || request.isSimulateServerError()) {
            log.error("[PaymentGatewayClient] Gateway outage active. Failing tx: {}", request.getTransactionId());
            throw new GatewayServiceUnavailableException("Payment Gateway is temporarily unavailable (HTTP 503)");
        }

        if (request.isSimulateTimeout()) {
            log.warn("[PaymentGatewayClient] Gateway latency spike triggered for tx: {}", request.getTransactionId());
            try {
                Thread.sleep(3500); // Exceeds 3000ms TimeLimiter threshold
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GatewayTimeoutException("Call interrupted during gateway latency spike", e);
            }
            throw new GatewayTimeoutException("Read timeout waiting for gateway response (Read timed out)");
        }

        // Simulate nominal 50ms latency
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String gatewayRef = "gw_ch_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        log.info("[PaymentGatewayClient] Gateway payment success for tx: {}, ref: {}", request.getTransactionId(), gatewayRef);
        return gatewayRef;
    }

    public void setForcedOutage(boolean outage) {
        this.forcedOutage.set(outage);
    }

    public boolean isForcedOutage() {
        return forcedOutage.get();
    }

    public int getCallCount() {
        return callCount.get();
    }

    public void resetCallCount() {
        callCount.set(0);
    }
}
