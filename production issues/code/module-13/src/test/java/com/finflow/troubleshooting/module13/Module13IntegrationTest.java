package com.finflow.troubleshooting.module13;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = Module13Application.class)
@AutoConfigureMockMvc
public class Module13IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testGetThreadPoolMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/async/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.corePoolSize").value(2))
                .andExpect(jsonPath("$.maximumPoolSize").value(4));
    }

    @Test
    void testAsyncGenerateStatementEndpoint() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/async/generate-statement?statementId=STMT-100&durationMs=10"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statementId").value("STMT-100"))
                .andExpect(jsonPath("$.result").exists());
    }
}
