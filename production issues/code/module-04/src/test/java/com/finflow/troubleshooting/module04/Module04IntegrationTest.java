package com.finflow.troubleshooting.module04;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module04Application.class)
@AutoConfigureMockMvc
public class Module04IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testValidPaymentProcessingSucceeds() throws Exception {
        String validJson = """
                {
                   "orderId": "ORD-999",
                   "amount": 499.99,
                   "currency": "USD",
                   "method": "CREDIT_CARD",
                   "timestamp": "2026-08-21T23:30:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.orderId").value("ORD-999"))
                .andExpect(jsonPath("$.amount").value(499.99))
                .andExpect(jsonPath("$.transactionId").isString());
    }
}
