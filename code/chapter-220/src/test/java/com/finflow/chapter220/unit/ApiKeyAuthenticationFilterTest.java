package com.finflow.chapter220.unit;

import com.finflow.chapter220.Chapter220Application;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Chapter220Application.class)
@AutoConfigureMockMvc
public class ApiKeyAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testPublicHealthEndpoint_accessibleWithoutCredentials() throws Exception {
        mockMvc.perform(get("/api/v1/public/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    public void testProtectedEndpoint_withoutApiKey_returnsUnauthorized401() throws Exception {
        mockMvc.perform(post("/api/v1/payments/MERCHANT_ACME/charge")
                        .param("amount", "100.00"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testProtectedEndpoint_withInvalidApiKey_returnsUnauthorized401() throws Exception {
        mockMvc.perform(post("/api/v1/payments/MERCHANT_ACME/charge")
                        .header("X-API-KEY", "key_invalid_random_string")
                        .param("amount", "100.00"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    @Test
    public void testProtectedEndpoint_withValidApiKey_authenticatesSuccessfully() throws Exception {
        mockMvc.perform(post("/api/v1/payments/MERCHANT_ACME/charge")
                        .header("X-API-KEY", "key_acme_admin_live")
                        .param("amount", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value("MERCHANT_ACME"))
                .andExpect(jsonPath("$.status").value("CHARGED"));
    }
}
