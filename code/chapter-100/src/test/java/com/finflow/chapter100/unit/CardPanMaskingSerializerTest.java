package com.finflow.chapter100.unit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finflow.chapter100.domain.CardDetails;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class CardPanMaskingSerializerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void testCardPanMasking() throws JsonProcessingException {
        CardDetails card = new CardDetails("John Doe", "4111222233334444", "12", "2030", "123");
        String json = mapper.writeValueAsString(card);
        
        assertTrue(json.contains("4111********4444"));
        assertFalse(json.contains("4111222233334444"));
    }

    @Test
    void testShortCardMasking() throws JsonProcessingException {
        CardDetails card = new CardDetails("John Doe", "12345", "12", "2030", "123");
        String json = mapper.writeValueAsString(card);
        
        assertTrue(json.contains("****"));
    }

    @Test
    void testNullCardMasking() throws JsonProcessingException {
        CardDetails card = new CardDetails("John Doe", null, "12", "2030", "123");
        String json = mapper.writeValueAsString(card);
        
        assertTrue(json.contains("\"cardNumber\":null"));
    }
}
