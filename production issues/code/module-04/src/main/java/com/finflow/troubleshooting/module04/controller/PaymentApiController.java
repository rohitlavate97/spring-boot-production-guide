package com.finflow.troubleshooting.module04.controller;

import com.finflow.troubleshooting.module04.model.PaymentRequest;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentApiController {

    @PostMapping(value = "/process", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> processPayment(@RequestBody PaymentRequest request) {
        String transactionId = "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return ResponseEntity.ok(Map.of(
                "transactionId", transactionId,
                "orderId", request.orderId(),
                "amount", request.amount(),
                "currency", request.currency(),
                "method", request.method().getValue(),
                "status", "SETTLED"
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getPaymentStatus(@PathVariable String id) {
        return ResponseEntity.ok(Map.of(
                "transactionId", id,
                "status", "SETTLED"
        ));
    }
}
