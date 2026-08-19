package com.finflow.chapter100.unit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finflow.chapter100.domain.events.ChargeSucceededEvent;
import com.finflow.chapter100.domain.events.RefundCreatedEvent;
import com.finflow.chapter100.domain.events.WebhookEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolymorphicEventSerializationTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
    }

    @Test
    void serializeChargeSucceededEvent() throws JsonProcessingException {
        ChargeSucceededEvent event = new ChargeSucceededEvent("evt_1", Instant.parse("2024-01-01T00:00:00Z"), "ch_1", 5000L, "USD");
        String json = mapper.writeValueAsString(event);
        assertTrue(json.contains("\"eventType\":\"CHARGE_SUCCEEDED\""));
    }

    @Test
    void deserializeChargeSucceededEvent() throws JsonProcessingException {
        String json = "{\"eventType\":\"CHARGE_SUCCEEDED\",\"eventId\":\"evt_1\",\"timestamp\":\"2024-01-01T00:00:00Z\",\"chargeId\":\"ch_1\",\"amountCents\":5000,\"currency\":\"USD\"}";
        WebhookEvent event = mapper.readValue(json, WebhookEvent.class);
        assertInstanceOf(ChargeSucceededEvent.class, event);
        assertEquals("evt_1", event.eventId());
    }

    @Test
    void deserializeRefundCreatedEvent() throws JsonProcessingException {
        String json = "{\"eventType\":\"REFUND_CREATED\",\"eventId\":\"evt_2\",\"timestamp\":\"2024-01-01T00:00:00Z\",\"refundId\":\"re_1\",\"originalChargeId\":\"ch_1\",\"amountCents\":2000,\"reason\":\"fraud\"}";
        WebhookEvent event = mapper.readValue(json, WebhookEvent.class);
        assertInstanceOf(RefundCreatedEvent.class, event);
        assertEquals("evt_2", event.eventId());
    }
}
