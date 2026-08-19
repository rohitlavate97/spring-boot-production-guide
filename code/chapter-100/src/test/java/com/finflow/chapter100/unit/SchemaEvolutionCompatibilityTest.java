package com.finflow.chapter100.unit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SchemaEvolutionCompatibilityTest {

    public static class TestEvent {
        public String eventId;
    }

    @Test
    void tolerateUnknownProperties() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        String json = "{\"eventId\":\"evt_123\", \"newBetaField\":\"value\", \"riskScore\":99}";
        
        TestEvent event = mapper.readValue(json, TestEvent.class);
        assertEquals("evt_123", event.eventId);
    }
}
