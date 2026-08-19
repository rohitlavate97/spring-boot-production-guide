package com.finflow.chapter070.unit;

import com.finflow.chapter070.correct.PaymentControllerCorrect;
import com.finflow.chapter070.correct.WebMvcConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentControllerCorrect.class)
@Import(WebMvcConfig.class) // Import config to wire up Interceptor and ArgumentResolver
class PaymentControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void processPayment_shouldReturnOkWithValidRequestAndHeader() throws Exception {
        String jsonPayload = """
                {
                    "paymentIntentId": "pi_123",
                    "amountCents": 5000,
                    "currency": "USD",
                    "paymentMethodId": "pm_123"
                }
                """;

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload)
                .header("X-Merchant-ID", "merch_abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.chargeId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.authorizedAmountCents").value(5000));
    }

    @Test
    void processPayment_shouldReturnBadRequestWhenPayloadInvalid() throws Exception {
        String jsonPayload = """
                {
                    "paymentIntentId": "",
                    "amountCents": 0,
                    "currency": "USD",
                    "paymentMethodId": "pm_123"
                }
                """;

        mockMvc.perform(post("/api/v1/payments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload)
                .header("X-Merchant-ID", "merch_abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void processPayment_shouldReturnServerErrorWhenExceptionOccurs() throws Exception {
        String jsonPayload = """
                {
                    "paymentIntentId": "pi_123",
                    "amountCents": 5000,
                    "currency": "USD",
                    "paymentMethodId": "pm_123"
                }
                """;

        // Interceptor will wrap, ArgumentResolver will resolve, but controller throws Exception.
        // Interceptor afterCompletion will still clean up!
        mockMvc.perform(post("/api/v1/payments/error")
                .contentType(MediaType.APPLICATION_JSON)
                .content(jsonPayload)
                .header("X-Merchant-ID", "merch_abc"))
                .andExpect(status().isInternalServerError());
    }
}
