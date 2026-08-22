package com.finflow.troubleshooting.module16;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ContainerDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/container/diagnostics returns container and JVM status")
    void testGetDiagnostics() throws Exception {
        mockMvc.perform(get("/api/v1/container/diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cgroupVersion").exists())
                .andExpect(jsonPath("$.jvmMaxHeapMb").isNumber())
                .andExpect(jsonPath("$.containerStatus").exists());
    }

    @Test
    @DisplayName("GET /api/v1/container/memory-budget calculates memory allocation breakdown")
    void testGetMemoryBudget() throws Exception {
        mockMvc.perform(get("/api/v1/container/memory-budget")
                        .param("containerMemoryMb", "2048")
                        .param("maxRamPercentage", "75.0")
                        .param("threadCount", "150"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.containerLimitMb").value(2048))
                .andExpect(jsonPath("$.maxHeapMb").value(1536))
                .andExpect(jsonPath("$.recommendedJvmFlags[0]").value(containsString("-XX:+UseContainerSupport")));
    }

    @Test
    @DisplayName("POST /api/v1/container/simulate-offheap and clear endpoints work")
    void testSimulateOffHeap() throws Exception {
        mockMvc.perform(post("/api/v1/container/simulate-offheap").param("mb", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OFF_HEAP_ALLOCATED"))
                .andExpect(jsonPath("$.allocatedMb").value(20));

        mockMvc.perform(post("/api/v1/container/clear-offheap"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLEARED"))
                .andExpect(jsonPath("$.totalSimulatedOffHeapMb").value(0));
    }

    @Test
    @DisplayName("POST /api/v1/container/simulate-cpu-work completes CPU computation")
    void testSimulateCpuWork() throws Exception {
        mockMvc.perform(post("/api/v1/container/simulate-cpu-work").param("iterations", "10000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.iterations").value(10000));
    }
}
