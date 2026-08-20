package com.finflow.chapter220.controller;

import com.finflow.chapter220.service.MerchantPaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class MerchantPaymentController {

    private final MerchantPaymentService paymentService;

    public MerchantPaymentController(MerchantPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @GetMapping("/public/health")
    public ResponseEntity<Map<String, String>> healthCheck() {
        return ResponseEntity.ok(Map.of("status", "UP", "service", "payment-security-service"));
    }

    @PostMapping("/payments/{merchantId}/charge")
    public ResponseEntity<MerchantPaymentService.ChargeResult> charge(
            @PathVariable String merchantId,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(paymentService.executeCharge(merchantId, amount));
    }

    @PostMapping("/payments/{merchantId}/refund")
    public ResponseEntity<MerchantPaymentService.RefundResult> refund(
            @PathVariable String merchantId,
            @RequestParam String chargeId,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(paymentService.executeRefund(merchantId, chargeId, amount));
    }

    @GetMapping("/payments/{merchantId}/audit")
    public ResponseEntity<Map<String, String>> audit(@PathVariable String merchantId) {
        return ResponseEntity.ok(Map.of("summary", paymentService.getAuditSummary(merchantId)));
    }
}
