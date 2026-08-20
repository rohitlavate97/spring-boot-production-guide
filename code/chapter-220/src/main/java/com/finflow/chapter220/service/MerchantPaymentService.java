package com.finflow.chapter220.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class MerchantPaymentService {

    public record ChargeResult(String chargeId, String merchantId, BigDecimal amount, String status) {}
    public record RefundResult(String refundId, String chargeId, String merchantId, BigDecimal amount, String status) {}

    /**
     * Tenant-Isolated Method Security for Charge Execution:
     * Requires PAYMENT:WRITE authority AND matching merchantId from the authenticated principal!
     */
    @PreAuthorize("hasAuthority('PAYMENT:WRITE') and #merchantId == authentication.principal.merchantId")
    public ChargeResult executeCharge(String merchantId, BigDecimal amount) {
        return new ChargeResult(
                "CHG-" + UUID.randomUUID().toString().substring(0, 8),
                merchantId,
                amount,
                "CHARGED"
        );
    }

    /**
     * Tenant-Isolated Role-Based Method Security for Refunds:
     * Requires ROLE_MERCHANT_ADMIN AND matching merchantId from the authenticated principal!
     */
    @PreAuthorize("hasRole('MERCHANT_ADMIN') and #merchantId == authentication.principal.merchantId")
    public RefundResult executeRefund(String merchantId, String chargeId, BigDecimal amount) {
        return new RefundResult(
                "REF-" + UUID.randomUUID().toString().substring(0, 8),
                chargeId,
                merchantId,
                amount,
                "REFUNDED"
        );
    }

    /**
     * Global Auditor or Merchant Read Permission:
     */
    @PreAuthorize("hasRole('AUDITOR') or (hasAuthority('PAYMENT:READ') and #merchantId == authentication.principal.merchantId)")
    public String getAuditSummary(String merchantId) {
        return "Audit report generated for: " + merchantId;
    }
}
