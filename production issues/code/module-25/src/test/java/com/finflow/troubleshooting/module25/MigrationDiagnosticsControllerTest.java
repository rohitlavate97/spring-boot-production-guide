package com.finflow.troubleshooting.module25;

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
class MigrationDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/migration/status returns Flyway history and data migration progress")
    void testGetStatus() throws Exception {
        mockMvc.perform(get("/api/v1/migration/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.flywayCleanDisabled").value(true))
                .andExpect(jsonPath("$.appliedMigrations").isArray())
                .andExpect(jsonPath("$.dataMigrationProgress").exists());
    }

    @Test
    @DisplayName("POST /api/v1/migration/dual-write creates account with both fields")
    void testDualWrite() throws Exception {
        mockMvc.perform(post("/api/v1/migration/dual-write")
                        .param("accountNumber", "ACC-REST-101")
                        .param("balance", "2500.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountNumber").value("ACC-REST-101"))
                .andExpect(jsonPath("$.accountUuid").exists());
    }

    @Test
    @DisplayName("GET /api/v1/migration/accounts returns all accounts")
    void testListAccounts() throws Exception {
        mockMvc.perform(get("/api/v1/migration/accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
