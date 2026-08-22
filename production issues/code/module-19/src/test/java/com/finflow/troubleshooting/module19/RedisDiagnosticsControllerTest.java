package com.finflow.troubleshooting.module19;

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
class RedisDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/cache/stats returns cache and database counters")
    void testGetStats() throws Exception {
        mockMvc.perform(get("/api/v1/cache/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cacheHits").exists())
                .andExpect(jsonPath("$.totalPostgresDbQueries").exists());
    }

    @Test
    @DisplayName("GET /api/v1/cache/exchange-rate returns exchange rate")
    void testGetExchangeRate() throws Exception {
        mockMvc.perform(get("/api/v1/cache/exchange-rate").param("pair", "USD_EUR"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pair").value("USD_EUR"))
                .andExpect(jsonPath("$.exchangeRate").value(0.9215));
    }

    @Test
    @DisplayName("GET /api/v1/cache/account returns 200 for existing and 404 for non-existing")
    void testGetAccount() throws Exception {
        mockMvc.perform(get("/api/v1/cache/account").param("accountId", "ACC-1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("ACC-1001"));

        mockMvc.perform(get("/api/v1/cache/account").param("accountId", "ACC-UNKNOWN"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("POST /api/v1/cache/simulate-stampede executes concurrent test successfully")
    void testSimulateStampede() throws Exception {
        mockMvc.perform(post("/api/v1/cache/simulate-stampede")
                        .param("pair", "USD_JPY")
                        .param("concurrentRequests", "5")
                        .param("useMutexGuard", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("STAMPEDE_PREVENTED"))
                .andExpect(jsonPath("$.totalDbHitsIncurred").value(1));
    }
}
