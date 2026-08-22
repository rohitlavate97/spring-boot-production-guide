package com.finflow.troubleshooting.module27;

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
class SagaDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/v1/saga/execute runs successful saga")
    void testExecuteSagaSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/saga/execute")
                        .param("accountId", "ACC-US-5500")
                        .param("amount", "1000.00")
                        .param("simulateFxFailure", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.sagaId").exists());
    }

    @Test
    @DisplayName("POST /api/v1/saga/execute with simulated failure runs reverse compensation")
    void testExecuteSagaCompensated() throws Exception {
        mockMvc.perform(post("/api/v1/saga/execute")
                        .param("accountId", "ACC-US-5500")
                        .param("amount", "1500.00")
                        .param("simulateFxFailure", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.finalStatus").value("COMPENSATED"))
                .andExpect(jsonPath("$.failureReason").exists());
    }

    @Test
    @DisplayName("GET /api/v1/saga/instances returns saga audit history")
    void testGetSagas() throws Exception {
        mockMvc.perform(get("/api/v1/saga/instances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
