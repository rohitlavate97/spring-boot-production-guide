package com.finflow.chapter090.correct;

import com.finflow.chapter090.domain.PaymentChargeRequest;
import com.finflow.chapter090.domain.PaymentChargeResponse;
import com.finflow.chapter090.exception.GatewayTimeoutException;
import com.finflow.chapter090.exception.IdempotencyConflictException;
import com.finflow.chapter090.exception.PaymentDeclinedException;
import com.finflow.chapter090.exception.ResourceNotFoundException;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentProcessingController {

    @PostMapping("/charge")
    public ResponseEntity<PaymentChargeResponse> charge(
            @Valid @RequestBody PaymentChargeRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        if ("conflict-key".equals(idempotencyKey)) {
            throw new IdempotencyConflictException(idempotencyKey);
        }
        
        if (request.amountCents() > 1000000) {
            throw new PaymentDeclinedException("Amount exceeds maximum allowed limit");
        }
        
        if ("fail-gateway".equals(request.paymentIntentId())) {
            throw new GatewayTimeoutException("Stripe", 30);
        }
        
        if ("unknown".equals(request.paymentIntentId())) {
            throw new ResourceNotFoundException("PaymentIntent", request.paymentIntentId());
        }
        
        PaymentChargeResponse response = new PaymentChargeResponse(
                UUID.randomUUID().toString(),
                "SUCCESS",
                request.amountCents(),
                Instant.now()
        );
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/unexpected")
    public ResponseEntity<String> unexpectedError() {
        throw new RuntimeException("Database connection dropped unexpectedly");
    }
}
