package com.finflow.troubleshooting.module20;

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
class KafkaDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/kafka/stats returns metrics")
    void testGetStats() throws Exception {
        mockMvc.perform(get("/api/v1/kafka/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.messagesProduced").exists())
                .andExpect(jsonPath("$.currentConsumerLag").exists());
    }

    @Test
    @DisplayName("POST /api/v1/kafka/produce and /consume endpoints work")
    void testProduceAndConsume() throws Exception {
        mockMvc.perform(post("/api/v1/kafka/produce")
                        .param("accountId", "ACC-12345")
                        .param("amount", "750.00")
                        .param("currency", "USD"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRODUCED"))
                .andExpect(jsonPath("$.event.amount").value(750.00));

        mockMvc.perform(post("/api/v1/kafka/consume"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONSUMED"))
                .andExpect(jsonPath("$.event.accountId").value("ACC-12345"));
    }

    @Test
    @DisplayName("POST /api/v1/kafka/produce-poison-pill routes corrupted record to DLT")
    void testProducePoisonPill() throws Exception {
        mockMvc.perform(post("/api/v1/kafka/produce-poison-pill")
                        .param("payload", "{\"bad_json\": ###}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("POISON_PILL_HANDLED"))
                .andExpect(jsonPath("$.dltTopic").value("payment-events.DLT"));
    }

    @Test
    @DisplayName("GET /api/v1/kafka/calculate-poll-budget returns budget calculation")
    void testCalculatePollBudget() throws Exception {
        mockMvc.perform(get("/api/v1/kafka/calculate-poll-budget")
                        .param("maxPollIntervalMs", "300000")
                        .param("p99ProcessingTimeMs", "200")
                        .param("configuredMaxPollRecords", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.safetyStatus").value("SAFE_POLL_BUDGET"));
    }
}
