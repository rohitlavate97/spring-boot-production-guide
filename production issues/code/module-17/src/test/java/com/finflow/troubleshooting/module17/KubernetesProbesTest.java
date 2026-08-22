package com.finflow.troubleshooting.module17;

import com.finflow.troubleshooting.module17.service.PodLifecycleService;
import com.finflow.troubleshooting.module17.service.SimulatedDownstreamDependencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class KubernetesProbesTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private SimulatedDownstreamDependencyService downstreamService;

    @Autowired
    private PodLifecycleService lifecycleService;

    @Test
    @DisplayName("Liveness and Readiness probes should both return 200 UP initially")
    void testInitialProbesUp() throws Exception {
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @DisplayName("Downstream database outage MUST turn Readiness DOWN (503) while Liveness remains UP (200)")
    void testDownstreamFailureProbeIsolation() throws Exception {
        try {
            // 1. Simulate downstream database pool timeout
            downstreamService.setDatabaseFailure(true);

            // 2. Readiness Probe must return 503 SERVICE_UNAVAILABLE (taking pod out of K8s Endpoints)
            mockMvc.perform(get("/actuator/health/readiness"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));

            // 3. CRITICAL: Liveness Probe MUST STILL return 200 UP to prevent Kubelet restart storm!
            mockMvc.perform(get("/actuator/health/liveness"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));

        } finally {
            downstreamService.setDatabaseFailure(false);
        }
    }

    @Test
    @DisplayName("Draining traffic via REFUSING_TRAFFIC marks Readiness DOWN (503) and Liveness UP (200)")
    void testDrainTrafficProbeBehavior() throws Exception {
        try {
            lifecycleService.drainTraffic();

            mockMvc.perform(get("/actuator/health/readiness"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));

            mockMvc.perform(get("/actuator/health/liveness"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        } finally {
            lifecycleService.acceptTraffic();
        }
    }
}
