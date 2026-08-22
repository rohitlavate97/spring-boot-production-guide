package com.finflow.troubleshooting.module17;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PodLifecycleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/lifecycle/status returns initial lifecycle state")
    void testGetStatus() throws Exception {
        mockMvc.perform(get("/api/v1/lifecycle/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.livenessState").value("CORRECT"))
                .andExpect(jsonPath("$.readinessState").value("ACCEPTING_TRAFFIC"))
                .andExpect(jsonPath("$.downstreamDbReachable").value(true));
    }

    @Test
    @DisplayName("POST /api/v1/lifecycle/drain-traffic and accept-traffic endpoints work")
    void testDrainAndAcceptTraffic() throws Exception {
        mockMvc.perform(post("/api/v1/lifecycle/drain-traffic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readinessState").value("REFUSING_TRAFFIC"));

        mockMvc.perform(post("/api/v1/lifecycle/accept-traffic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.readinessState").value("ACCEPTING_TRAFFIC"));
    }

    @Test
    @DisplayName("POST /api/v1/orders/checkout completes payment settlement")
    void testCheckoutEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/orders/checkout").param("delayMs", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.transactionId").exists())
                .andExpect(jsonPath("$.amount").value(250.00));
    }
}
