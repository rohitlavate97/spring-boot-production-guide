package com.finflow.chapter240.service;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class MerchantPayoutService {

    public record PayoutResponse(String payoutId, String merchantId, BigDecimal amount, String status) {}

    /**
     * Standard Payout: Requires SCOPE_payment:write AND matching merchant_id claim in JWT!
     */
    @PreAuthorize("hasAuthority('SCOPE_payment:write') and #merchantId == authentication.tokenAttributes['merchant_id']")
    public PayoutResponse processPayout(String merchantId, BigDecimal amount) {
        return new PayoutResponse(
                "PO-" + UUID.randomUUID().toString().substring(0, 8),
                merchantId,
                amount,
                "PROCESSED"
        );
    }

    /**
     * High Value Payout: Requires SCOPE_payout:execute AND ROLE_MERCHANT_ADMIN AND matching merchant_id!
     */
    @PreAuthorize("hasAuthority('SCOPE_payout:execute') and hasRole('MERCHANT_ADMIN') and #merchantId == authentication.tokenAttributes['merchant_id']")
    public PayoutResponse executeHighValuePayout(String merchantId, BigDecimal amount) {
        return new PayoutResponse(
                "PO-HV-" + UUID.randomUUID().toString().substring(0, 8),
                merchantId,
                amount,
                "EXECUTED_HIGH_VALUE"
        );
    }
}
