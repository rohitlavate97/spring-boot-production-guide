package com.finflow.troubleshooting.module11;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module11Application.class)
@AutoConfigureMockMvc
public class CorsPreflightSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testCorsPreflightOptionsRequestSucceedsWithoutAuthentication() throws Exception {
        mockMvc.perform(options("/api/v1/secure/profile")
                        .header("Origin", "https://merchant.finflow.com")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://merchant.finflow.com"))
                .andExpect(header().exists("Access-Control-Allow-Methods"));
    }
}
