package com.finflow.troubleshooting.module01;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module01Application.class)
@AutoConfigureMockMvc
public class DecoupledOrderFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testDecoupledOrderAndNotificationFlow() throws Exception {
        // 1. Process order checkout
        mockMvc.perform(post("/api/v1/orders/checkout?customerId=CUST-777&amount=250.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").isString())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.paymentId").isString());

        // 2. Verify decoupled event listener received event and processed notification
        mockMvc.perform(get("/api/v1/orders/notifications/count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificationCount").value(1));
    }
}
