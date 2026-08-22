package com.finflow.troubleshooting.module28;

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
class IncidentResponseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/v1/incident/triage returns SEV1 decision and action plan")
    void testTriageEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/incident/triage")
                        .param("errorRatePercent", "8.0")
                        .param("p99LatencyMs", "2500.0")
                        .param("isRevenueImpacting", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentSeverity").value("SEV1_CRITICAL"))
                .andExpect(jsonPath("$.targetSlaMinutes").value(15))
                .andExpect(jsonPath("$.matchedArchetypes").isArray());
    }

    @Test
    @DisplayName("GET /api/v1/incident/scenarios returns all 20 scenarios")
    void testGetScenarios() throws Exception {
        mockMvc.perform(get("/api/v1/incident/scenarios"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(20));
    }

    @Test
    @DisplayName("GET /api/v1/incident/scenarios/1 returns scenario 1 details")
    void testGetScenarioById() throws Exception {
        mockMvc.perform(get("/api/v1/incident/scenarios/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scenarioId").value(1))
                .andExpect(jsonPath("$.title").value("JVM Native Memory Leak & Glibc Arena Fragmentation"));
    }
}
