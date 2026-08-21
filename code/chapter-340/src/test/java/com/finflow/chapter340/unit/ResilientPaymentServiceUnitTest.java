package com.finflow.chapter340.unit;

import com.finflow.chapter340.Chapter340Application;
import com.finflow.chapter340.client.PaymentGatewayClient;
import com.finflow.chapter340.domain.PaymentRequest;
import com.finflow.chapter340.domain.PaymentResponse;
import com.finflow.chapter340.domain.PaymentStatus;
import com.finflow.chapter340.exception.PaymentValidationException;
import com.finflow.chapter340.service.ResilientPaymentService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = Chapter340Application.class)
public class ResilientPaymentServiceUnitTest {

    @Autowired
    private ResilientPaymentService paymentService;

    @Autowired
    private PaymentGatewayClient gatewayClient;

    @BeforeEach
    void setUp() {
        gatewayClient.setForcedOutage(false);
        gatewayClient.resetCallCount();
        paymentService.getCircuitBreaker().reset();
    }

    @Test
    void testSuccessfulPaymentProcessingInClosedState() {
        PaymentRequest request = new PaymentRequest("TX-101", "MERCH-A", BigDecimal.valueOf(100.00), "USD", "tok_visa");

        PaymentResponse response = paymentService.processPayment(request);

        assertThat(response.getStatus()).isEqualTo(PaymentStatus.SUCCESS);
        assertThat(response.getGatewayReference()).startsWith("gw_ch_");
        assertThat(paymentService.getCircuitBreakerState()).isEqualTo("CLOSED");
    }

    @Test
    void testCircuitBreakerTripsToOpenAndExecutesFallback() {
        // Force gateway outage
        gatewayClient.setForcedOutage(true);

        PaymentRequest req = new PaymentRequest("TX-FAIL", "MERCH-A", BigDecimal.valueOf(50.00), "USD", "tok_visa");

        // Send 5 calls to exceed minimumNumberOfCalls (5) with 100% failure rate
        for (int i = 0; i < 5; i++) {
            PaymentResponse resp = paymentService.processPayment(req);
            assertThat(resp.getStatus()).isEqualTo(PaymentStatus.FALLBACK_QUEUED);
        }

        // Circuit breaker should now be OPEN
        assertThat(paymentService.getCircuitBreakerState()).isEqualTo(CircuitBreaker.State.OPEN.name());

        int gatewayCallsBeforeFastFail = gatewayClient.getCallCount();

        // 6th call should immediately fast-fail via CallNotPermittedException fallback without touching gateway
        PaymentResponse fastFailResp = paymentService.processPayment(req);
        assertThat(fastFailResp.getStatus()).isEqualTo(PaymentStatus.FALLBACK_QUEUED);
        assertThat(fastFailResp.getMessage()).contains("Circuit Breaker OPEN fast-fail");

        // Verify gateway was NOT invoked during fast-fail
        assertThat(gatewayClient.getCallCount()).isEqualTo(gatewayCallsBeforeFastFail);
    }

    @Test
    void testValidationExceptionBypassesCircuitBreakerAndThrowsDirectly() {
        PaymentRequest invalidReq = new PaymentRequest("TX-INVALID", "MERCH-A", BigDecimal.valueOf(-10.00), "USD", "tok_visa");

        assertThrows(PaymentValidationException.class, () -> paymentService.processPayment(invalidReq));

        // Circuit Breaker state should remain CLOSED with 0 recorded failures
        assertThat(paymentService.getCircuitBreakerState()).isEqualTo("CLOSED");
        assertThat(paymentService.getCircuitBreaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(0);
    }
}
