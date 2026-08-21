package com.finflow.troubleshooting.module12;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module12Application.class)
@AutoConfigureMockMvc
public class Module12IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testAssessCreditSuccessEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/payments/assess-credit?customerId=CUST-INT-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUST-INT-1"))
                .andExpect(jsonPath("$.creditScore").value(780))
                .andExpect(jsonPath("$.fallbackUsed").value(false));
    }

    @Test
    void testAssessCreditFallbackEndpointOnFailure() throws Exception {
        mockMvc.perform(get("/api/v1/payments/assess-credit?customerId=CUST-INT-2&simulateFailure=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerId").value("CUST-INT-2"))
                .andExpect(jsonPath("$.riskCategory").value("MANUAL_REVIEW_FALLBACK"))
                .andExpect(jsonPath("$.fallbackUsed").value(true));
    }
}
