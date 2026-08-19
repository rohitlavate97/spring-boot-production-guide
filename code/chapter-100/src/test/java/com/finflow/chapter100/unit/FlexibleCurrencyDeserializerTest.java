package com.finflow.chapter100.unit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.finflow.chapter100.correct.jackson.FlexibleCurrencyDeserializer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FlexibleCurrencyDeserializerTest {

    private final ObjectMapper mapper = new ObjectMapper();

    public static class TestAmount {
        @JsonDeserialize(using = FlexibleCurrencyDeserializer.class)
        public Long amount;
    }

    @Test
    void deserializeFromInteger() throws JsonProcessingException {
        String json = "{\"amount\": 5000}";
        TestAmount result = mapper.readValue(json, TestAmount.class);
        assertEquals(5000L, result.amount);
    }

    @Test
    void deserializeFromStringDecimal() throws JsonProcessingException {
        String json = "{\"amount\": \"50.00\"}";
        TestAmount result = mapper.readValue(json, TestAmount.class);
        assertEquals(5000L, result.amount);
    }

    @Test
    void deserializeFromStringInteger() throws JsonProcessingException {
        String json = "{\"amount\": \"5000\"}";
        TestAmount result = mapper.readValue(json, TestAmount.class);
        assertEquals(5000L, result.amount);
    }

    @Test
    void deserializeFromObject() throws JsonProcessingException {
        String json = "{\"amount\": {\"amountCents\": 5000}}";
        TestAmount result = mapper.readValue(json, TestAmount.class);
        assertEquals(5000L, result.amount);
    }
}
