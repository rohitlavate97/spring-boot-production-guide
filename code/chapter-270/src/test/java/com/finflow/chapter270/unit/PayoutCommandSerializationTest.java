package com.finflow.chapter270.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.finflow.chapter270.domain.PayoutCommand;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

public class PayoutCommandSerializationTest {

    @Test
    public void testSerialization_roundTrip_succeeds() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());

        PayoutCommand payout = new PayoutCommand(
                "PO-100",
                "MERCHANT_ACME",
                BigDecimal.valueOf(1250.50),
                "USD",
                "INSTANT",
                "US89370400440532013000",
                "PENDING",
                0,
                Instant.now()
        );

        String json = mapper.writeValueAsString(payout);
        assertThat(json).contains("PO-100", "MERCHANT_ACME", "1250.5");

        PayoutCommand deserialized = mapper.readValue(json, PayoutCommand.class);
        assertThat(deserialized.getPayoutId()).isEqualTo("PO-100");
        assertThat(deserialized.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(1250.50));
        assertThat(deserialized.getPayoutType()).isEqualTo("INSTANT");
    }
}
