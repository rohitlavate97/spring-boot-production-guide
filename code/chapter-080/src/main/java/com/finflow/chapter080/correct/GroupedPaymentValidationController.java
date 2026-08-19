package com.finflow.chapter080.correct;

import com.finflow.chapter080.domain.BankTransferGroup;
import com.finflow.chapter080.domain.CardPaymentGroup;
import com.finflow.chapter080.domain.PaymentIntentRequest;
import com.finflow.chapter080.domain.PaymentIntentResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/grouped-payments")
public class GroupedPaymentValidationController {

    @PostMapping("/card")
    public ResponseEntity<PaymentIntentResponse> processCardPayment(
            @Validated(CardPaymentGroup.class) @RequestBody PaymentIntentRequest request) {
        
        PaymentIntentResponse response = new PaymentIntentResponse(
            request.intentId(),
            "CARD_PROCESSED",
            request.amountCents(),
            request.currency()
        );
        return ResponseEntity.ok(response);
    }

    @PostMapping("/bank")
    public ResponseEntity<PaymentIntentResponse> processBankTransfer(
            @Validated(BankTransferGroup.class) @RequestBody PaymentIntentRequest request) {
        
        PaymentIntentResponse response = new PaymentIntentResponse(
            request.intentId(),
            "BANK_TRANSFER_INITIATED",
            request.amountCents(),
            request.currency()
        );
        return ResponseEntity.ok(response);
    }
}
