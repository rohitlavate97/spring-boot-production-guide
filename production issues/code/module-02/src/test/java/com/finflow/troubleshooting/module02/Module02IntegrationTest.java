package com.finflow.troubleshooting.module02;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module02Application.class)
@AutoConfigureMockMvc
public class Module02IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testConfigDiagnosticsEndpoints() throws Exception {
        // 1. Inspect current bound configuration
        mockMvc.perform(get("/api/v1/config/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gatewayUrl").value("https://api-dev.finflow.com/v1"))
                .andExpect(jsonPath("$.timeoutMs").value(5000))
                .andExpect(jsonPath("$.maxRetries").value(3))
                .andExpect(jsonPath("$.apiKeyMasked").value("******"));

        // 2. Inspect property origin and lineage
        mockMvc.perform(get("/api/v1/config/inspect?key=finflow.core.timeout-ms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.propertyKey").value("finflow.core.timeout-ms"))
                .andExpect(jsonPath("$.winningActiveValue").value("5000"))
                .andExpect(jsonPath("$.winningSource").isString());
    }
}
