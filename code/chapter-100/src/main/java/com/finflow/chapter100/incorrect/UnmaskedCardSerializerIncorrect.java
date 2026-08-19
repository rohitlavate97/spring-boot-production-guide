package com.finflow.chapter100.incorrect;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;

public class UnmaskedCardSerializerIncorrect extends JsonSerializer<String> {

    @Override
    public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        // INCORRECT: Exposes raw PAN directly.
        gen.writeString(value);
    }
}
