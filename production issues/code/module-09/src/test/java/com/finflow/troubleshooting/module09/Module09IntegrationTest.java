package com.finflow.troubleshooting.module09;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module09Application.class)
@AutoConfigureMockMvc
public class Module09IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testGetPoolMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/pool/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.poolName").value("FinFlowHikariPool"))
                .andExpect(jsonPath("$.maxPoolSize").value(3));
    }

    @Test
    void testSettlePaymentEndpoint() throws Exception {
        mockMvc.perform(post("/api/v1/pool/settle?amount=250.00&delayMs=0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.transactionId").isString());
    }
}
