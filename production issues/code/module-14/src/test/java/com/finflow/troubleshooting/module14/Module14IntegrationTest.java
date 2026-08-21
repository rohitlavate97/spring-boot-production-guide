package com.finflow.troubleshooting.module14;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module14Application.class)
@AutoConfigureMockMvc
public class Module14IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testGetMemoryStatsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/memory/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.heapUsedBytes").isNumber())
                .andExpect(jsonPath("$.nonHeapUsedBytes").isNumber());
    }

    @Test
    void testPutAndGetBoundedCacheEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/memory/cache/put?key=order-101&value=SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.key").value("order-101"))
                .andExpect(jsonPath("$.maxCapacity").value(5));

        mockMvc.perform(get("/api/v1/memory/cache/get?key=order-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("SUCCESS"));
    }

    @Test
    void testInjectLeakEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/memory/leak?key=tx-chunk-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LEAKED_1MB"));
    }
}
