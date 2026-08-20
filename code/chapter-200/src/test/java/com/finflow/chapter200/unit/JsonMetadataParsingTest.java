package com.finflow.chapter200.unit;

import com.finflow.chapter200.Chapter200Application;
import com.finflow.chapter200.correct.SettlementQueryServiceCorrect;
import com.finflow.chapter200.domain.SettlementTransaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter200Application.class)
public class JsonMetadataParsingTest {

    @Autowired
    private SettlementQueryServiceCorrect queryService;

    @Test
    public void testExtractRoutingCode_parsesValidJson() {
        SettlementTransaction tx = new SettlementTransaction(
                UUID.randomUUID(),
                "MERCHANT_JSON_1",
                "SETTLED",
                BigDecimal.valueOf(500.00),
                "USD",
                "GTW-JSON",
                Instant.now(),
                "{\"routing_code\": \"SWIFT_CHASE_NY\", \"risk_score\": 12}"
        );

        Optional<String> routingCode = queryService.extractRoutingCodeFromMetadata(tx);

        assertThat(routingCode).isPresent();
        assertThat(routingCode.get()).isEqualTo("SWIFT_CHASE_NY");
    }

    @Test
    public void testExtractRoutingCode_handlesNullOrMissingGracefully() {
        SettlementTransaction tx = new SettlementTransaction(
                UUID.randomUUID(),
                "MERCHANT_JSON_2",
                "SETTLED",
                BigDecimal.valueOf(500.00),
                "USD",
                "GTW-JSON-2",
                Instant.now(),
                "{}"
        );

        Optional<String> routingCode = queryService.extractRoutingCodeFromMetadata(tx);
        assertThat(routingCode).isEmpty();
    }
}
