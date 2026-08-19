package com.finflow.chapter080.correct;

import com.finflow.chapter080.domain.PaymentIntentRequest;
import com.finflow.chapter080.domain.PaymentIntentResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentValidationController {

    @PostMapping("/intent")
    public ResponseEntity<PaymentIntentResponse> createIntent(@Valid @RequestBody PaymentIntentRequest request) {
        // Validation has already occurred successfully at this point.
        PaymentIntentResponse response = new PaymentIntentResponse(
            request.intentId(),
            "CREATED",
            request.amountCents(),
            request.currency()
        );
        return ResponseEntity.ok(response);
    }
}
