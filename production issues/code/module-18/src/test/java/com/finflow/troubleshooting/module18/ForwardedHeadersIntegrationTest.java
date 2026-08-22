package com.finflow.troubleshooting.module18;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ForwardedHeadersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Should properly resolve X-Forwarded-Proto, X-Forwarded-Host, and X-Forwarded-For")
    void testForwardedHeadersResolution() throws Exception {
        mockMvc.perform(get("/api/v1/headers/echo")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "api.finflow.com")
                        .header("X-Forwarded-For", "203.0.113.195")
                        .header("X-Forwarded-Port", "443"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.scheme").value("https"))
                .andExpect(jsonPath("$.serverName").value("api.finflow.com"))
                .andExpect(jsonPath("$.isSecure").value(true));
    }
}
