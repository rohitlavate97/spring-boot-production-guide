package com.finflow.troubleshooting.module21;

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
class ConcurrencyDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/wallet/balance returns account balance")
    void testGetBalance() throws Exception {
        mockMvc.perform(get("/api/v1/wallet/balance").param("accountId", "ACC-101"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").value("ACC-101"))
                .andExpect(jsonPath("$.atomicCasBalance").value(500.00));
    }

    @Test
    @DisplayName("POST /api/v1/wallet/simulate-race-condition with Atomic CAS prevents double spending")
    void testSimulateRaceConditionWithCas() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/simulate-race-condition")
                        .param("accountId", "ACC-101")
                        .param("concurrentRequests", "10")
                        .param("debitAmount", "100.00")
                        .param("useAtomicCas", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultStatus").value("RACE_PREVENTED_CORRECT"))
                .andExpect(jsonPath("$.successfulDebitsCount").value(5))
                .andExpect(jsonPath("$.finalRemainingBalance").value(0.00));
    }

    @Test
    @DisplayName("POST /api/v1/wallet/simulate-lock-release-trap with safe Lua release prevents foreign lock release")
    void testSimulateLockReleaseTrap() throws Exception {
        mockMvc.perform(post("/api/v1/wallet/simulate-lock-release-trap")
                        .param("useSafeLuaRelease", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.processA_ReleaseSuccess").value(false))
                .andExpect(jsonPath("$.outcome").value("LUA_SCRIPT_BLOCKED_UNSAFE_RELEASE_OF_PROCESS_B_LOCK"));
    }
}
