package com.finflow.chapter390.integration;

import com.finflow.chapter390.Chapter390Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Chapter390Application.class)
@AutoConfigureMockMvc
public class PerformanceTuningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testBenchmarkEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/performance/benchmark?iterations=200&concurrency=2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.iterations").value(200))
                .andExpect(jsonPath("$.concurrency").value(2))
                .andExpect(jsonPath("$.speedupFactor").isNumber())
                .andExpect(jsonPath("$.optimizationSummary").isString());
    }

    @Test
    void testGcMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/performance/gc-metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.collectorName").isString())
                .andExpect(jsonPath("$.heapUsedMb").isNumber())
                .andExpect(jsonPath("$.memoryPoolUsageMb").isMap());
    }

    @Test
    void testJfrLifecycleEndpoints() throws Exception {
        // Start JFR
        mockMvc.perform(post("/api/v1/performance/jfr/start?name=integration-test-rec"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STARTED"));

        // Status JFR
        mockMvc.perform(get("/api/v1/performance/jfr/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));

        // Stop JFR
        mockMvc.perform(post("/api/v1/performance/jfr/stop"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STOPPED_AND_DUMPED"));
    }
}
