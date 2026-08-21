package com.finflow.chapter340.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.chapter340.Chapter340Application;
import com.finflow.chapter340.client.PaymentGatewayClient;
import com.finflow.chapter340.domain.PaymentRequest;
import com.finflow.chapter340.service.ResilientPaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Chapter340Application.class)
@AutoConfigureMockMvc
public class PaymentCircuitBreakerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentGatewayClient gatewayClient;

    @Autowired
    private ResilientPaymentService paymentService;

    @BeforeEach
    void setUp() {
        gatewayClient.setForcedOutage(false);
        gatewayClient.resetCallCount();
        paymentService.getCircuitBreaker().reset();
    }

    @Test
    void testProcessPaymentEndpointSuccess() throws Exception {
        PaymentRequest request = new PaymentRequest("TX-INT-1", "MERCH-PROD", BigDecimal.valueOf(250.00), "USD", "tok_mastercard");

        mockMvc.perform(post("/api/v1/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.circuitBreakerState").value("CLOSED"))
                .andExpect(jsonPath("$.gatewayReference").value(startsWith("gw_ch_")));
    }

    @Test
    void testSimulateOutageAndCircuitBreakerStateTransitions() throws Exception {
        // 1. Verify initial circuit breaker state is CLOSED
        mockMvc.perform(get("/api/v1/payments/circuit-breaker/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("CLOSED"));

        // 2. Enable simulated outage
        mockMvc.perform(post("/api/v1/payments/simulate-outage?enabled=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.simulatedOutage").value(true));

        PaymentRequest request = new PaymentRequest("TX-INT-FAIL", "MERCH-PROD", BigDecimal.valueOf(199.99), "USD", "tok_visa");

        // 3. Send 5 failed payments to trigger circuit breaker open threshold
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/v1/payments/process")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("FALLBACK_QUEUED"));
        }

        // 4. Verify circuit breaker state is now OPEN
        mockMvc.perform(get("/api/v1/payments/circuit-breaker/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("OPEN"))
                .andExpect(jsonPath("$.failureRate").value(100.0));

        // 5. Test Actuator CircuitBreakers endpoint reflects OPEN state
        mockMvc.perform(get("/actuator/circuitbreakers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.circuitBreakers.stripeGateway.state").value("OPEN"));
    }
}
