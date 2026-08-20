package com.finflow.chapter310.unit;

import com.finflow.chapter310.Chapter310Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Chapter310Application.class)
@AutoConfigureMockMvc
public class DynamicLivenessTransitionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testDynamicLivenessTransition_breakAndRecover() throws Exception {
        // 1. Initially liveness is UP (200)
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        // 2. Break liveness (e.g. fatal unrecoverable deadlock)
        mockMvc.perform(post("/api/v1/payments/liveness/break")
                        .param("reason", "Simulated unrecoverable deadlock"))
                .andExpect(status().isOk());

        // 3. Actuator liveness probe returns HTTP 503 DOWN (Kubelet restarts container)
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));

        // 4. Recover liveness
        mockMvc.perform(post("/api/v1/payments/liveness/correct")
                        .param("reason", "Recovered"))
                .andExpect(status().isOk());

        // 5. Liveness probe recovers to HTTP 200 UP
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
