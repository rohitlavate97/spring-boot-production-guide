package com.finflow.chapter100.correct.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class CardPanMaskingSerializer extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        if (value == null) {
            gen.writeNull();
            return;
        }
        String cleanValue = value.replaceAll("\\s+", "");
        if (cleanValue.length() < 8) {
            gen.writeString("****");
            return;
        }
        
        String prefix = cleanValue.substring(0, 4);
        String suffix = cleanValue.substring(cleanValue.length() - 4);
        String masked = prefix + "*".repeat(cleanValue.length() - 8) + suffix;
        gen.writeString(masked);
    }
}
