package com.finflow.chapter100.unit;

import com.finflow.chapter100.correct.PaymentWebhookController;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PaymentWebhookController.class)
class PaymentWebhookControllerMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testHandleChargeSucceededWebhook() throws Exception {
        String json = "{\"eventType\":\"CHARGE_SUCCEEDED\",\"eventId\":\"evt_1\",\"timestamp\":\"2024-01-01T00:00:00Z\",\"chargeId\":\"ch_1\",\"amountCents\":5000,\"currency\":\"USD\"}";

        mockMvc.perform(post("/api/webhooks")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value(true))
                .andExpect(jsonPath("$.eventId").value("evt_1"));
    }
}
