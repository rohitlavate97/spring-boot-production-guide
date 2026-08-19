package com.finflow.chapter070.correct;

import com.finflow.chapter070.domain.MerchantContext;
import com.finflow.chapter070.domain.PaymentRequest;
import com.finflow.chapter070.domain.PaymentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentControllerCorrect {

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @CurrentMerchant MerchantContext merchantContext,
            @Valid @RequestBody PaymentRequest request) {
        
        // Correct: Clean controller, focused purely on business logic.
        // Interceptor and argument resolver handle the boilerplate setup and teardown.
        PaymentResponse response = new PaymentResponse(
                UUID.randomUUID().toString(),
                "COMPLETED",
                request.amountCents(),
                Instant.now()
        );
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/error")
    public ResponseEntity<PaymentResponse> processPaymentWithError(
            @CurrentMerchant MerchantContext merchantContext,
            @Valid @RequestBody PaymentRequest request) {
        throw new RuntimeException("Simulated error in controller");
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<String> handleException(RuntimeException ex) {
        return ResponseEntity.internalServerError().body(ex.getMessage());
    }
}
