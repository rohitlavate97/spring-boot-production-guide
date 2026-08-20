package com.finflow.chapter240.unit;

import com.finflow.chapter240.Chapter240Application;
import com.finflow.chapter240.security.MockJwtTokenFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Chapter240Application.class)
@AutoConfigureMockMvc
public class OAuth2ResourceServerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MockJwtTokenFactory tokenFactory;

    @Test
    public void testProtectedEndpoint_withoutToken_returns401Unauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/payouts/MERCHANT_ACME/process")
                        .param("amount", "500.00"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testProtectedEndpoint_withValidScopeAndMatchingTenant_returns200Ok() throws Exception {
        // Token has SCOPE_payment:write and merchant_id=MERCHANT_ACME
        String token = tokenFactory.createToken(
                "USER_POS_1",
                "MERCHANT_ACME",
                "client-pos-1",
                "payment:write",
                List.of("ROLE_MERCHANT_STAFF")
        );

        mockMvc.perform(post("/api/v1/payouts/MERCHANT_ACME/process")
                        .header("Authorization", "Bearer " + token)
                        .param("amount", "500.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.merchantId").value("MERCHANT_ACME"))
                .andExpect(jsonPath("$.status").value("PROCESSED"));
    }

    @Test
    public void testProtectedEndpoint_withMismatchedTenant_returns403Forbidden() throws Exception {
        // Token belongs to MERCHANT_ACME, but URL targets MERCHANT_BETA
        String token = tokenFactory.createToken(
                "USER_POS_1",
                "MERCHANT_ACME",
                "client-pos-1",
                "payment:write",
                List.of("ROLE_MERCHANT_STAFF")
        );

        mockMvc.perform(post("/api/v1/payouts/MERCHANT_BETA/process")
                        .header("Authorization", "Bearer " + token)
                        .param("amount", "500.00"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testHighValuePayout_withoutRequiredScopeAndRole_returns403Forbidden() throws Exception {
        // Staff has SCOPE_payment:write but lacks SCOPE_payout:execute and ROLE_MERCHANT_ADMIN
        String staffToken = tokenFactory.createToken(
                "USER_STAFF_1",
                "MERCHANT_ACME",
                "client-pos-1",
                "payment:write",
                List.of("ROLE_MERCHANT_STAFF")
        );

        mockMvc.perform(post("/api/v1/payouts/MERCHANT_ACME/execute-high-value")
                        .header("Authorization", "Bearer " + staffToken)
                        .param("amount", "50000.00"))
                .andExpect(status().isForbidden());

        // Admin has SCOPE_payout:execute and ROLE_MERCHANT_ADMIN -> succeeds!
        String adminToken = tokenFactory.createToken(
                "USER_ADMIN_1",
                "MERCHANT_ACME",
                "client-admin-1",
                "payment:write payout:execute",
                List.of("ROLE_MERCHANT_ADMIN")
        );

        mockMvc.perform(post("/api/v1/payouts/MERCHANT_ACME/execute-high-value")
                        .header("Authorization", "Bearer " + adminToken)
                        .param("amount", "50000.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("EXECUTED_HIGH_VALUE"));
    }
}
