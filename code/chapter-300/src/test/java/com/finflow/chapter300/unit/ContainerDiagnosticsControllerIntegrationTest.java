package com.finflow.chapter300.unit;

import com.finflow.chapter300.Chapter300Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Chapter300Application.class)
@AutoConfigureMockMvc
public class ContainerDiagnosticsControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testGetDiagnostics_returnsOkAndMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/container/diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.availableProcessors").isNumber())
                .andExpect(jsonPath("$.maxMemoryMb").isNumber())
                .andExpect(jsonPath("$.jvmVersion").isNotEmpty());
    }

    @Test
    public void testMemoryLayoutCalculator_returnsOptimalLayout() throws Exception {
        mockMvc.perform(post("/api/v1/container/memory-layout-calculator")
                        .param("containerLimitMb", "4096")
                        .param("maxRamPercentage", "75.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.containerLimitMb").value(4096))
                .andExpect(jsonPath("$.calculatedHeapLimitMb").value(3072))
                .andExpect(jsonPath("$.offHeapBufferMb").value(1024))
                .andExpect(jsonPath("$.recommendation").value(org.hamcrest.Matchers.containsString("OPTIMAL")));
    }
}
