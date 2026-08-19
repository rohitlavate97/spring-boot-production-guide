package com.finflow.chapter100.correct.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;

public class FlexibleCurrencyDeserializer extends JsonDeserializer<Long> {

    @Override
    public Long deserialize(JsonParser p, DeserializationContext ctxt) throws IOException, JsonProcessingException {
        JsonNode node = p.getCodec().readTree(p);
        
        if (node.isNumber()) {
            return node.asLong();
        } else if (node.isTextual()) {
            String text = node.asText();
            if (text.contains(".")) {
                double val = Double.parseDouble(text);
                return Math.round(val * 100);
            }
            return Long.parseLong(text);
        } else if (node.isObject() && node.has("amountCents")) {
            return node.get("amountCents").asLong();
        }
        throw new IllegalArgumentException("Cannot deserialize flexible currency from: " + node.toString());
    }
}
