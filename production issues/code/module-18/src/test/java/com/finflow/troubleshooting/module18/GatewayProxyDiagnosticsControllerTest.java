package com.finflow.troubleshooting.module18;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class GatewayProxyDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/gateway/diagnostics returns configured timeout settings")
    void testGetDiagnostics() throws Exception {
        mockMvc.perform(get("/api/v1/gateway/diagnostics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tomcatKeepAliveTimeoutSec").value(70))
                .andExpect(jsonPath("$.nginxKeepAliveTimeoutSec").value(65))
                .andExpect(jsonPath("$.keepAliveSafety").value("SAFE"));
    }

    @Test
    @DisplayName("GET /api/v1/gateway/validate-timeouts calculates safety status")
    void testValidateTimeouts() throws Exception {
        mockMvc.perform(get("/api/v1/gateway/validate-timeouts")
                        .param("clientTimeoutMs", "15000")
                        .param("gatewayTimeoutMs", "10000")
                        .param("downstreamApiTimeoutMs", "8000")
                        .param("nginxKeepaliveTimeoutSec", "65")
                        .param("tomcatKeepaliveTimeoutSec", "70"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keepAliveSafetyStatus").value("SAFE_KEEP_ALIVE_HIERARCHY"))
                .andExpect(jsonPath("$.timeoutHierarchyStatus").value("VALID_TIMEOUT_HIERARCHY"));
    }

    @Test
    @DisplayName("POST /api/v1/payments/authorize executes successfully")
    void testAuthorizePayment() throws Exception {
        mockMvc.perform(post("/api/v1/payments/authorize")
                        .param("delayMs", "10")
                        .param("amount", "250.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORIZED"))
                .andExpect(jsonPath("$.authCode").exists())
                .andExpect(jsonPath("$.amount").value(250.00));
    }

    @Test
    @DisplayName("GET /api/v1/statements/export generates large dataset for buffer testing")
    void testExportStatement() throws Exception {
        mockMvc.perform(get("/api/v1/statements/export").param("sizeKb", "16"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("ACC-99887766"))
                .andExpect(jsonPath("$.totalRecords", greaterThanOrEqualTo(80)))
                .andExpect(jsonPath("$.records").isArray());
    }
}
