package com.finflow.troubleshooting.module15;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module15Application.class)
@AutoConfigureMockMvc
public class Module15IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testSubmitOrderEndpointPropagatesCorrelationId() throws Exception {
        MvcResult mvcResult = mockMvc.perform(post("/api/v1/orders/submit?orderId=ORD-MOCK-1")
                        .header("X-Correlation-ID", "CORR-MOCK-777"))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value("ORD-MOCK-1"))
                .andExpect(jsonPath("$.correlationId").value("CORR-MOCK-777"))
                .andExpect(jsonPath("$.asyncResult").value("PROCESSED:ORD-MOCK-1:CORR:CORR-MOCK-777"));
    }
}
