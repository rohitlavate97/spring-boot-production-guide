package com.finflow.troubleshooting.module24;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TimezoneDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /api/v1/time/current returns UTC timezone configuration")
    void testGetCurrent() throws Exception {
        mockMvc.perform(get("/api/v1/time/current"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.utcInstant").exists())
                .andExpect(jsonPath("$.jvmDefaultTimezone").value("UTC"));
    }

    @Test
    @DisplayName("GET /api/v1/time/convert-timezone formats time for given zone")
    void testConvertTimezone() throws Exception {
        mockMvc.perform(get("/api/v1/time/convert-timezone")
                        .param("isoTimestamp", "2026-08-22T10:00:00Z")
                        .param("targetZoneId", "America/New_York"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.targetZoneId").value("America/New_York"))
                .andExpect(jsonPath("$.formattedDateTime").exists());
    }

    @Test
    @DisplayName("POST /api/v1/time/validate-token validates with leeway")
    void testValidateToken() throws Exception {
        long validFutureExpiry = Instant.now().plusSeconds(60).getEpochSecond();

        mockMvc.perform(post("/api/v1/time/validate-token")
                        .param("tokenExpiryEpochSec", String.valueOf(validFutureExpiry))
                        .param("clockSkewToleranceSec", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValidWithoutLeeway").value(true));
    }
}
