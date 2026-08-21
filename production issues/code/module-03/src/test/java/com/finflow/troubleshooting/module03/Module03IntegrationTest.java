package com.finflow.troubleshooting.module03;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module03Application.class)
@AutoConfigureMockMvc
public class Module03IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testSigningAndInspectionEndpoints() throws Exception {
        // 1. Test cryptographic payload signing
        mockMvc.perform(post("/api/v1/dependencies/sign")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("test-transaction-payload-2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.payloadSha256").isString())
                .andExpect(jsonPath("$.streamSha256").isString());

        // 2. Test classloader origin inspection
        mockMvc.perform(get("/api/v1/dependencies/inspect-class?className=org.apache.commons.codec.digest.DigestUtils"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classFound").value(true))
                .andExpect(jsonPath("$.loadedJarLocation").isString());
    }
}
