package com.finflow.chapter330.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.chapter330.Chapter330Application;
import com.finflow.chapter330.domain.PaymentTraceRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Chapter330Application.class)
@AutoConfigureMockMvc
@AutoConfigureObservability
public class PaymentTracingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testCheckoutTrace_autoGeneratesTraceAndSpanId() throws Exception {
        PaymentTraceRequest request = new PaymentTraceRequest(
                "PAY-TEST-1", "MERCH-ALPHA", "USD", BigDecimal.valueOf(250.0), "CUST-99"
        );

        mockMvc.perform(post("/api/v1/payments/trace/checkout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(jsonPath("$.traceId", not(is("NO_TRACE"))))
                .andExpect(jsonPath("$.spanId").isNotEmpty())
                .andExpect(jsonPath("$.fraudCheckDecision").value("APPROVED"));
    }

    @Test
    public void testW3CTraceparentHeader_propagatesIncomingTraceId() throws Exception {
        PaymentTraceRequest request = new PaymentTraceRequest(
                "PAY-TEST-2", "MERCH-BETA", "USD", BigDecimal.valueOf(150.0), "CUST-42"
        );

        // W3C Traceparent: version(00)-traceId(4bf92f3577b34da6a3ce929d0e0e4736)-parentId(00f067aa0ba902b7)-traceFlags(01)
        String incomingTraceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String traceparentHeader = "00-" + incomingTraceId + "-00f067aa0ba902b7-01";

        mockMvc.perform(post("/api/v1/payments/trace/checkout")
                        .header("traceparent", traceparentHeader)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.traceId").value(incomingTraceId))
                .andExpect(jsonPath("$.baggageMerchantId").value("MERCH-BETA"));
    }
}
