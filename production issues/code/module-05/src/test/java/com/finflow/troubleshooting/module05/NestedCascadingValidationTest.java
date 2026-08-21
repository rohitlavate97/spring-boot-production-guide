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
public class NestedCascadingValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testInvalidNestedItemFailsValidation() throws Exception {
        String invalidNestedJson = """
                {
                   "customerId": "CUST-1001",
                   "authPin": "FinFlow@2026",
                   "items": [
                      {
                         "sku": "",
                         "quantity": 0,
                         "price": 19.99
                      }
                   ]
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidNestedJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failure (400)"))
                .andExpect(jsonPath("$.invalidFields['items[0].sku']").isNotEmpty())
                .andExpect(jsonPath("$.invalidFields['items[0].quantity']").value("Item quantity must be at least 1"));
    }
}
