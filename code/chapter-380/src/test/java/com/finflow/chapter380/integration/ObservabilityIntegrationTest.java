package com.finflow.chapter380.integration;

import com.finflow.chapter380.Chapter380Application;
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

@SpringBootTest(classes = Chapter380Application.class)
@AutoConfigureMockMvc
public class ObservabilityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testLogPaymentEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/observability/log-payment?orderId=ORD-INT-1&amount=120.00&cardPan=4111-2222-3333-4444&success=true&durationMs=45"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LOGGED"))
                .andExpect(jsonPath("$.orderId").value("ORD-INT-1"));
    }

    @Test
    void testTriageRunbookEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/observability/runbook/triage?incidentId=INC-TEST-01&errorRate=8.0&p99Latency=3000.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.incidentId").value("INC-TEST-01"))
                .andExpect(jsonPath("$.severity").value("SEV_1"))
                .andExpect(jsonPath("$.status").value("INVESTIGATING"))
                .andExpect(jsonPath("$.mitigationActions").isArray());
    }

    @Test
    void testDiagnosticsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/observability/diagnostics?errorRate=0.5&p99Latency=80.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.activeThreadCount").isNumber())
                .andExpect(jsonPath("$.totalMemoryMb").isNumber())
                .andExpect(jsonPath("$.currentErrorRatePercent").value(0.5));
    }

    @Test
    void testPrometheusActuatorEndpointExposed() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk());
    }
}
