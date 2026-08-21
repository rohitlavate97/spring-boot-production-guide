package com.finflow.chapter360.integration;

import com.finflow.chapter360.Chapter360Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Chapter360Application.class)
@AutoConfigureMockMvc
public class PaymentConfigControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testGetPropertiesEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/config/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionFeePercent").value(2.5))
                .andExpect(jsonPath("$.fixedFeeCents").value(30))
                .andExpect(jsonPath("$.environmentTier").value("PRODUCTION"));
    }

    @Test
    void testCalculateFeeEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/config/fees/calculate?amount=200.00&txId=TX-INT-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TX-INT-1"))
                .andExpect(jsonPath("$.grossAmount").value(200.00))
                .andExpect(jsonPath("$.percentageFee").value(5.00))
                .andExpect(jsonPath("$.fixedFee").value(0.30))
                .andExpect(jsonPath("$.totalFee").value(5.30))
                .andExpect(jsonPath("$.netPayoutAmount").value(194.70));
    }

    @Test
    void testUpdateFeePropertiesAndRecalculate() throws Exception {
        // 1. Update properties dynamically
        mockMvc.perform(post("/api/v1/config/update-fees?percent=3.5&fixedCents=50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPDATED"))
                .andExpect(jsonPath("$.newFeePercent").value(3.5))
                .andExpect(jsonPath("$.newFixedCents").value(50));

        // 2. Recalculate with updated fee rates
        mockMvc.perform(get("/api/v1/config/fees/calculate?amount=100.00&txId=TX-INT-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentageFee").value(3.50))
                .andExpect(jsonPath("$.fixedFee").value(0.50))
                .andExpect(jsonPath("$.totalFee").value(4.00))
                .andExpect(jsonPath("$.netPayoutAmount").value(96.00));
    }

    @Test
    void testServiceDiscoveryEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/discovery/services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.order-service").isArray())
                .andExpect(jsonPath("$.ledger-service").isArray());

        mockMvc.perform(get("/api/v1/discovery/choose/order-service"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.serviceId").value("order-service"))
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
