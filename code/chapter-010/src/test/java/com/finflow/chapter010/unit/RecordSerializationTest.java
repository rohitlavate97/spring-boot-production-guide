package com.finflow.chapter010.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finflow.chapter010.domain.PaymentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RecordSerializationTest {

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void record_serializesAndDeserializesCorrectly() throws Exception {
        Instant now = Instant.now();
        UUID intentId = UUID.randomUUID();
        
        PaymentResponse response = new PaymentResponse(
                intentId,
                5000,
                "USD",
                "COMPLETED",
                now
        );

        String json = objectMapper.writeValueAsString(response);
        assertNotNull(json);
        
        PaymentResponse deserialized = objectMapper.readValue(json, PaymentResponse.class);
        
        assertEquals(intentId, deserialized.paymentIntentId());
        assertEquals(5000, deserialized.amountCents());
        assertEquals("USD", deserialized.currency());
        assertEquals("COMPLETED", deserialized.status());
        assertEquals(now, deserialized.createdAt());
        
        // Test immutability via record generated equals/hashcode
        assertEquals(response, deserialized);
    }
}
