package com.finflow.chapter090.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.chapter090.correct.PaymentProcessingController;
import com.finflow.chapter090.correct.GlobalExceptionHandler;
import com.finflow.chapter090.domain.PaymentChargeRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = PaymentProcessingController.class)
class ProblemDetailResponseMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testPaymentDeclinedExceptionReturns422ProblemDetail() throws Exception {
        PaymentChargeRequest request = new PaymentChargeRequest("pi_123", 2000000L, "USD", "pm_123");

        mockMvc.perform(post("/api/v1/payments/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.type").value("https://api.finflow.com/errors/payment_declined"))
                .andExpect(jsonPath("$.detail").value("Payment declined: Amount exceeds maximum allowed limit"))
                .andExpect(jsonPath("$.errorCode").value("PAYMENT_DECLINED"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void testGatewayTimeoutExceptionReturns504ProblemDetailWithRetryHeader() throws Exception {
        PaymentChargeRequest request = new PaymentChargeRequest("fail-gateway", 1000L, "USD", "pm_123");

        mockMvc.perform(post("/api/v1/payments/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isGatewayTimeout())
                .andExpect(header().string(HttpHeaders.RETRY_AFTER, "30"))
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("GATEWAY_TIMEOUT"))
                .andExpect(jsonPath("$.errorCode").value("GATEWAY_TIMEOUT"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void testIdempotencyConflictExceptionReturns409ProblemDetail() throws Exception {
        PaymentChargeRequest request = new PaymentChargeRequest("pi_123", 1000L, "USD", "pm_123");

        mockMvc.perform(post("/api/v1/payments/charge")
                .header("Idempotency-Key", "conflict-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.errorCode").value("IDEMPOTENCY_CONFLICT"))
                .andExpect(jsonPath("$.traceId").exists());
    }

    @Test
    void testValidationErrorReturns400ProblemDetailWithInvalidParams() throws Exception {
        PaymentChargeRequest request = new PaymentChargeRequest("", 0L, "", "");

        mockMvc.perform(post("/api/v1/payments/charge")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("INVALID_REQUEST"))
                .andExpect(jsonPath("$.detail").value("Invalid request parameters"))
                .andExpect(jsonPath("$.invalidParams", hasSize(4)))
                .andExpect(jsonPath("$.traceId").exists());
    }
}
