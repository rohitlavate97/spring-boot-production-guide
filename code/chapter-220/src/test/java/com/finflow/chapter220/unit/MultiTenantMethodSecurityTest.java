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
public class MultiTenantMethodSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testCrossTenantAccess_isBlockedWith403Forbidden() throws Exception {
        // Merchant ACME Admin tries to execute charge on MERCHANT_BETA endpoint -> 403 Forbidden!
        mockMvc.perform(post("/api/v1/payments/MERCHANT_BETA/charge")
                        .header("X-API-KEY", "key_acme_admin_live")
                        .param("amount", "250.00"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testRoleBasedAuthorization_staffCannotRefund() throws Exception {
        // ACME Staff has PAYMENT:WRITE (can charge)
        mockMvc.perform(post("/api/v1/payments/MERCHANT_ACME/charge")
                        .header("X-API-KEY", "key_acme_staff_live")
                        .param("amount", "50.00"))
                .andExpect(status().isOk());

        // But ACME Staff does NOT have ROLE_MERCHANT_ADMIN -> cannot refund (403 Forbidden)!
        mockMvc.perform(post("/api/v1/payments/MERCHANT_ACME/refund")
                        .header("X-API-KEY", "key_acme_staff_live")
                        .param("chargeId", "CHG-100")
                        .param("amount", "50.00"))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testAdmin_canRefundOwnMerchant() throws Exception {
        // ACME Admin can refund ACME
        mockMvc.perform(post("/api/v1/payments/MERCHANT_ACME/refund")
                        .header("X-API-KEY", "key_acme_admin_live")
                        .param("chargeId", "CHG-200")
                        .param("amount", "100.00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REFUNDED"));
    }

    @Test
    public void testGlobalAuditor_canReadAuditAcrossMerchants_butCannotCharge() throws Exception {
        // Auditor can read ACME audit
        mockMvc.perform(get("/api/v1/payments/MERCHANT_ACME/audit")
                        .header("X-API-KEY", "key_auditor_global"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Audit report generated for: MERCHANT_ACME"));

        // Auditor cannot charge (403 Forbidden)
        mockMvc.perform(post("/api/v1/payments/MERCHANT_ACME/charge")
                        .header("X-API-KEY", "key_auditor_global")
                        .param("amount", "500.00"))
                .andExpect(status().isForbidden());
    }
}
