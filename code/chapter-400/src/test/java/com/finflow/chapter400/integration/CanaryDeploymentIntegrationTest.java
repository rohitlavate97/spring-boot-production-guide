package com.finflow.chapter400.integration;

import com.finflow.chapter400.Chapter400Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Chapter400Application.class)
@AutoConfigureMockMvc
public class CanaryDeploymentIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testVersionEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/deployment/version"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value("v3.0.0"))
                .andExpect(jsonPath("$.environment").value("production"))
                .andExpect(jsonPath("$.strategy").value("CANARY_PROGRESSIVE"))
                .andExpect(jsonPath("$.activeFeatureFlags").isMap());
    }

    @Test
    void testFeatureEvaluationAndKillSwitch() throws Exception {
        // 1. Evaluate feature for whitelist user
        mockMvc.perform(get("/api/v1/deployment/features/evaluate?flagKey=v3_smart_routing_engine&userId=user_vip_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true));

        // 2. Trigger kill-switch
        mockMvc.perform(post("/api/v1/deployment/features/toggle?flagKey=v3_smart_routing_engine&killSwitch=true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("KILL_SWITCH_ACTIVE"));

        // 3. Re-evaluate feature - must now be FALSE
        mockMvc.perform(get("/api/v1/deployment/features/evaluate?flagKey=v3_smart_routing_engine&userId=user_vip_1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    void testVerifyCanaryStepEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/deployment/verify-canary?version=v3.0.0&trafficWeight=20&errorRate=0.15&p99Latency=48.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deploymentVersion").value("v3.0.0"))
                .andExpect(jsonPath("$.promotionApproved").value(true))
                .andExpect(jsonPath("$.smokeTestsPassed").value(true));
    }

    @Test
    void testActuatorHealthProbesConfigured() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
