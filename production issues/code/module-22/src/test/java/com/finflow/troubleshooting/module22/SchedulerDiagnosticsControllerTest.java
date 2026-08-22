package com.finflow.troubleshooting.module22;

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
class SchedulerDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/scheduler/stats returns scheduler pool configuration")
    void testGetStats() throws Exception {
        mockMvc.perform(get("/api/v1/scheduler/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schedulerPoolSize").value(8))
                .andExpect(jsonPath("$.shedLockStats").exists());
    }

    @Test
    @DisplayName("POST /api/v1/scheduler/simulate-cluster-run with ShedLock executes exactly 1 run")
    void testSimulateClusterRunWithShedLock() throws Exception {
        mockMvc.perform(post("/api/v1/scheduler/simulate-cluster-run")
                        .param("clusterPodsCount", "6")
                        .param("useShedLock", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("DEDUPLICATION_SUCCESSFUL_EXACTLY_ONE_RUN"))
                .andExpect(jsonPath("$.totalExecutionsTriggered").value(1))
                .andExpect(jsonPath("$.preventedDuplicateRuns").value(5));
    }
}
