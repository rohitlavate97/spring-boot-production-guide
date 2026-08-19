package com.finflow.chapter100.correct;

import com.fasterxml.jackson.annotation.JsonView;
import com.finflow.chapter100.domain.CardDetails;
import com.finflow.chapter100.domain.PaymentIntent;
import com.finflow.chapter100.domain.Views;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/intents")
public class PaymentIntentController {

    @GetMapping("/{id}")
    @JsonView(Views.PublicView.class)
    public PaymentIntent getIntent(@PathVariable String id) {
        return createMockIntent(id);
    }

    @GetMapping("/{id}/audit")
    @JsonView(Views.InternalAuditView.class)
    public PaymentIntent getAuditIntent(@PathVariable String id) {
        return createMockIntent(id);
    }

    private PaymentIntent createMockIntent(String id) {
        CardDetails cardDetails = new CardDetails("John Doe", "4111222233334444", "12", "2030", "123");
        return new PaymentIntent(id, 5000L, "USD", Instant.parse("2024-01-01T00:00:00Z"), cardDetails);
    }
}
