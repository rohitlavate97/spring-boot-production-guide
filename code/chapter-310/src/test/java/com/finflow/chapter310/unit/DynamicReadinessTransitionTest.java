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
public class DynamicReadinessTransitionTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testDynamicReadinessTransition_refuseAndAccept() throws Exception {
        // 1. Initially readiness is UP (200)
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));

        // 2. Refuse traffic (e.g. during graceful drain or queue overload)
        mockMvc.perform(post("/api/v1/payments/readiness/refuse")
                        .param("reason", "Simulated queue overload"))
                .andExpect(status().isOk());

        // 3. Actuator readiness probe returns HTTP 503 OUT_OF_SERVICE (Kubernetes stops sending new traffic)
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("OUT_OF_SERVICE"));

        // 4. Accept traffic back
        mockMvc.perform(post("/api/v1/payments/readiness/accept")
                        .param("reason", "Overload resolved"))
                .andExpect(status().isOk());

        // 5. Readiness probe recovers to HTTP 200 UP
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
