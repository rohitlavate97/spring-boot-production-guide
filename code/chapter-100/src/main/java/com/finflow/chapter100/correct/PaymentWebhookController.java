package com.finflow.chapter100.correct;

import com.finflow.chapter100.domain.events.WebhookEvent;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
public class PaymentWebhookController {

    @PostMapping
    public ResponseEntity<?> handleWebhook(@RequestBody WebhookEvent event) {
        return ResponseEntity.ok(Map.of("received", true, "eventId", event.eventId()));
    }
}
