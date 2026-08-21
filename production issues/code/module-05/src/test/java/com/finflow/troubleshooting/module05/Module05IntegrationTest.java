package com.finflow.troubleshooting.module05;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module05Application.class)
@AutoConfigureMockMvc
public class Module05IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testValidOrderCreationSucceeds() throws Exception {
        String validOrderJson = """
                {
                   "customerId": "CUST-VALID-1",
                   "authPin": "FinFlow@2026",
                   "items": [
                      {
                         "sku": "SKU-IPHONE-15",
                         "quantity": 1,
                         "price": 999.99
                      },
                      {
                         "sku": "SKU-CASE-AIR",
                         "quantity": 2,
                         "price": 29.99
                      }
                   ]
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validOrderJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.customerId").value("CUST-VALID-1"))
                .andExpect(jsonPath("$.itemCount").value(2))
                .andExpect(jsonPath("$.orderId").isString());
    }
}
