package com.finflow.chapter240.controller;

import com.finflow.chapter240.service.MerchantPayoutService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1")
public class MerchantPayoutController {

    private final MerchantPayoutService payoutService;

    public MerchantPayoutController(MerchantPayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @PostMapping("/payouts/{merchantId}/process")
    public ResponseEntity<MerchantPayoutService.PayoutResponse> processPayout(
            @PathVariable String merchantId,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(payoutService.processPayout(merchantId, amount));
    }

    @PostMapping("/payouts/{merchantId}/execute-high-value")
    public ResponseEntity<MerchantPayoutService.PayoutResponse> executeHighValue(
            @PathVariable String merchantId,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(payoutService.executeHighValuePayout(merchantId, amount));
    }
}
