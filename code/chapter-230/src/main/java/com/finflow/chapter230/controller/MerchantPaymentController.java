package com.finflow.chapter230.controller;

import com.finflow.chapter230.service.MerchantPaymentService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1")
public class MerchantPaymentController {

    private final MerchantPaymentService paymentService;

    public MerchantPaymentController(MerchantPaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/payments/{merchantId}/execute")
    public ResponseEntity<MerchantPaymentService.PaymentResult> executePayment(
            @PathVariable String merchantId,
            @RequestParam BigDecimal amount) {
        return ResponseEntity.ok(paymentService.executePayment(merchantId, amount));
    }
}
