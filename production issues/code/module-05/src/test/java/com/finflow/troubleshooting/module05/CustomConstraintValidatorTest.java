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
public class CustomConstraintValidatorTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testWeakPasswordFailsCustomConstraintValidation() throws Exception {
        String weakPasswordJson = """
                {
                   "customerId": "CUST-1002",
                   "authPin": "weakpass",
                   "items": [
                      {
                         "sku": "SKU-ABC-1",
                         "quantity": 2,
                         "price": 49.99
                      }
                   ]
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(weakPasswordJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Validation Failure (400)"))
                .andExpect(jsonPath("$.invalidFields.authPin").value("Authorization PIN/Password is weak"));
    }
}
