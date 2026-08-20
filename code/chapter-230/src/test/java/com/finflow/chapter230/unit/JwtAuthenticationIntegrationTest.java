package com.finflow.chapter230.unit;

import com.finflow.chapter230.Chapter230Application;
import com.finflow.chapter230.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Chapter230Application.class)
@AutoConfigureMockMvc
public class JwtAuthenticationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Test
    public void testProtectedEndpoint_withoutToken_returns401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/payments/MERCHANT_ACME/execute")
                        .param("amount", "100.00"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testProtectedEndpoint_withValidToken_returns200Ok() throws Exception {
        String token = jwtTokenProvider.generateAccessToken("USER_101", "MERCHANT_ACME", List.of("PAYMENT:WRITE"));

        mockMvc.perform(post("/api/v1/payments/MERCHANT_ACME/execute")
                        .header("Authorization", "Bearer " + token)
                        .param("amount", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value("MERCHANT_ACME"))
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    public void testProtectedEndpoint_withTokenFromDifferentMerchant_returns403Forbidden() throws Exception {
        // Token belongs to MERCHANT_ACME, but request path targets MERCHANT_BETA
        String token = jwtTokenProvider.generateAccessToken("USER_101", "MERCHANT_ACME", List.of("PAYMENT:WRITE"));

        mockMvc.perform(post("/api/v1/payments/MERCHANT_BETA/execute")
                        .header("Authorization", "Bearer " + token)
                        .param("amount", "100.00"))
                .andExpect(status().isForbidden());
    }
}
