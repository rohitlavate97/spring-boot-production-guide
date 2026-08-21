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
public class JacksonSerializationTrapTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testMalformedJsonPayloadReturns400() throws Exception {
        String malformedJson = """
                {
                   "orderId": "ORD-123",
                   "amount": "NOT_A_NUMBER",
                   "method": "CREDIT_CARD"
                }
                """;

        mockMvc.perform(post("/api/v1/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(malformedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request Body (400)"));
    }

    @Test
    void testInvalidEnumValueReturns400WithDescriptiveMessage() throws Exception {
        String invalidEnumJson = """
                {
                   "orderId": "ORD-456",
                   "amount": 100.50,
                   "currency": "USD",
                   "method": "BARTER_TRADE",
                   "timestamp": "2026-08-21T18:00:00Z"
                }
                """;

        mockMvc.perform(post("/api/v1/payments/process")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidEnumJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request Body (400)"));
    }
}
