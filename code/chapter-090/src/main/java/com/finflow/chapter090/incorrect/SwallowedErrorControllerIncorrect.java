package com.finflow.chapter090.incorrect;

import com.finflow.chapter090.domain.PaymentChargeRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/incorrect/payments")
public class SwallowedErrorControllerIncorrect {

    @PostMapping("/charge")
    public ResponseEntity<?> charge(@RequestBody PaymentChargeRequest request) {
        try {
            // Simulated payment failure
            throw new RuntimeException("Payment gateway declined transaction due to insufficient funds");
        } catch (Exception e) {
            // ANTI-PATTERN: Returning HTTP 200 OK for an error, with a custom body
            return ResponseEntity.ok(Map.of(
                    "status", "FAILED",
                    "message", e.getMessage()
            ));
        }
    }
}
