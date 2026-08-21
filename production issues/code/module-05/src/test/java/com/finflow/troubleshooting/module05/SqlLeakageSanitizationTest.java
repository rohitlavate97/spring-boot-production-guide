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
public class SqlLeakageSanitizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testDatabaseConstraintErrorIsSanitizedAndDoesNotLeakSql() throws Exception {
        String dbCollisionJson = """
                {
                   "customerId": "DB_COLLISION",
                   "authPin": "FinFlow@2026",
                   "items": [
                      {
                         "sku": "SKU-COLLISION",
                         "quantity": 1,
                         "price": 99.00
                      }
                   ]
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(dbCollisionJson))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Data Integrity Conflict (409)"))
                .andExpect(jsonPath("$.detail").value("The requested operation violates a unique data constraint (e.g. duplicate key or conflicting entity state)."))
                // Ensure internal DB details are NOT exposed to client
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("finflow_orders_tbl"))))
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("uk_orders_reference_id"))));
    }
}
