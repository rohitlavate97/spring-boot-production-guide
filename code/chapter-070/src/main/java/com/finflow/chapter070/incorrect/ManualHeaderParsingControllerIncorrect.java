package com.finflow.chapter070.incorrect;

import com.finflow.chapter070.domain.MerchantContext;
import com.finflow.chapter070.domain.PaymentRequest;
import com.finflow.chapter070.domain.PaymentResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/incorrect/payments")
public class ManualHeaderParsingControllerIncorrect {

    @PostMapping
    public ResponseEntity<PaymentResponse> processPayment(
            @RequestBody PaymentRequest request,
            HttpServletRequest servletRequest) {
        
        // Incorrect: Boilerplate code scattered inside the controller.
        // Hard to test, duplicates validation logic, pollutes business logic.
        String merchantId = servletRequest.getHeader("X-Merchant-ID");
        if (merchantId == null || merchantId.isBlank()) {
            return ResponseEntity.status(401).build();
        }
        MerchantContext context = new MerchantContext(merchantId, "STANDARD", "dummy-key", "US");

        PaymentResponse response = new PaymentResponse(
                UUID.randomUUID().toString(),
                "COMPLETED",
                request.amountCents(),
                Instant.now()
        );
        return ResponseEntity.ok(response);
    }
}
