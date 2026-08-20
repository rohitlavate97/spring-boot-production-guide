package com.finflow.chapter260.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finflow.chapter260.domain.PaymentEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class PaymentEventSerializationTest {

    @Test
    public void testSerialization_roundTrip_succeeds() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        PaymentEvent event = new PaymentEvent(
                "EVT-100",
                "PAY-200",
                "MERCHANT_ACME",
                BigDecimal.valueOf(150.75),
                "USD",
                "AUTHORIZED",
                Instant.now(),
                "IDEMP-KEY-999"
        );

        String json = mapper.writeValueAsString(event);
        assertThat(json).contains("EVT-100", "MERCHANT_ACME", "150.75");

        PaymentEvent deserialized = mapper.readValue(json, PaymentEvent.class);
        assertThat(deserialized.getEventId()).isEqualTo("EVT-100");
        assertThat(deserialized.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(150.75));
    }
}
