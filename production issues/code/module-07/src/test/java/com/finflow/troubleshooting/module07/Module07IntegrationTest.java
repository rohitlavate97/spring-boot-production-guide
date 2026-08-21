package com.finflow.troubleshooting.module07;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module07Application.class)
@AutoConfigureMockMvc
public class Module07IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testSwallowedBugEndpointExecution() throws Exception {
        mockMvc.perform(post("/api/v1/banking/transfer/swallowed-bug?from=ACC-REST-1&to=ACC-REST-2&amount=100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Executed with swallowed exception bug"));

        mockMvc.perform(get("/api/v1/banking/accounts/ACC-REST-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(900.00));
    }
}
