package com.finflow.troubleshooting.module26;

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
class DeploymentDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/deploy/route returns routing decision and affinity cookie")
    void testRouteRequest() throws Exception {
        mockMvc.perform(get("/api/v1/deploy/route").param("userId", "USR-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.selectedVersion").exists())
                .andExpect(jsonPath("$.assignedAffinityCookie").exists());
    }

    @Test
    @DisplayName("GET /api/v1/deploy/canary-health returns ACA health report")
    void testCanaryHealth() throws Exception {
        mockMvc.perform(get("/api/v1/deploy/canary-health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decision").exists());
    }

    @Test
    @DisplayName("POST /api/v1/deploy/simulate-anomaly injects errors and triggers rollback")
    void testSimulateAnomaly() throws Exception {
        mockMvc.perform(post("/api/v1/deploy/simulate-anomaly")
                        .param("failedCanaryRequests", "20")
                        .param("highLatencyMs", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultingHealthDecision").value("AUTOMATED_ROLLBACK_TRIGGERED"));
    }
}
