package com.finflow.chapter080.unit;

import com.finflow.chapter080.correct.PaymentValidationController;
import com.finflow.chapter080.correct.ValidationExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentValidationController.class)
@Import(ValidationExceptionHandler.class)
class PaymentValidationControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void validPayloadShouldReturn200() throws Exception {
        String validJson = """
                {
                  "intentId": "pi_12345",
                  "amountCents": 1000,
                  "currency": "USD",
                  "paymentMethodType": "CRYPTO",
                  "splitAllocations": [
                    {
                      "merchantId": "m_1",
                      "amountCents": 1000
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/payments/intent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intentId").value("pi_12345"))
                .andExpect(jsonPath("$.status").value("CREATED"));
    }

    @Test
    void invalidPayloadShouldReturn400WithErrors() throws Exception {
        String invalidJson = """
                {
                  "intentId": "",
                  "amountCents": -10,
                  "currency": "usd",
                  "paymentMethodType": "CARD",
                  "splitAllocations": []
                }
                """;

        // intentId blank, amount < 1, currency invalid format, card number missing for CARD
        mockMvc.perform(post("/api/v1/payments/intent")
                .contentType(MediaType.APPLICATION_JSON)
                .content(invalidJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.fieldErrors").isArray());
    }
}
