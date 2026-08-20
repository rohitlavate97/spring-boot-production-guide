package com.finflow.chapter200.unit;

import com.finflow.chapter200.Chapter200Application;
import com.finflow.chapter200.correct.SettlementQueryServiceCorrect;
import com.finflow.chapter200.domain.SettlementTransaction;
import com.finflow.chapter200.repository.SettlementTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter200Application.class)
public class CompositeIndexQueryTest {

    @Autowired
    private SettlementTransactionRepository repository;

    @Autowired
    private SettlementQueryServiceCorrect queryService;

    private final String targetMerchant = "MERCHANT_ACME_001";
    private final String otherMerchant = "MERCHANT_BETA_002";
    private Instant now;

    @BeforeEach
    public void setup() {
        repository.deleteAll();
        now = Instant.now();

        // 3 settlements for target merchant: 2 SETTLED, 1 PENDING
        repository.save(new SettlementTransaction(
                UUID.randomUUID(), targetMerchant, "SETTLED", BigDecimal.valueOf(150.00), "USD", "GTW-101",
                now.minus(2, ChronoUnit.HOURS), "{\"routing_code\": \"FEDWIRE\"}"
        ));
        repository.save(new SettlementTransaction(
                UUID.randomUUID(), targetMerchant, "SETTLED", BigDecimal.valueOf(350.00), "USD", "GTW-102",
                now.minus(1, ChronoUnit.HOURS), "{\"routing_code\": \"ACH\"}"
        ));
        repository.save(new SettlementTransaction(
                UUID.randomUUID(), targetMerchant, "PENDING", BigDecimal.valueOf(500.00), "USD", "GTW-103",
                now.minus(30, ChronoUnit.MINUTES), "{\"routing_code\": \"SEPA\"}"
        ));

        // 1 settlement for other merchant
        repository.save(new SettlementTransaction(
                UUID.randomUUID(), otherMerchant, "SETTLED", BigDecimal.valueOf(999.00), "USD", "GTW-201",
                now.minus(1, ChronoUnit.HOURS), "{}"
        ));
        repository.flush();
    }

    @Test
    public void testLeftmostPrefixQuery_fetchesExactMatchesOrderedByDate() {
        Instant start = now.minus(3, ChronoUnit.HOURS);
        Instant end = now;

        List<SettlementTransaction> results = queryService.findMerchantSettlements(targetMerchant, "SETTLED", start, end);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getGatewayReference()).isEqualTo("GTW-102"); // Newer first
        assertThat(results.get(1).getGatewayReference()).isEqualTo("GTW-101");
    }

    @Test
    public void testPrefixSubsetQuery_fetchesAllByMerchantAndStatus() {
        List<SettlementTransaction> results = queryService.findByMerchantAndStatus(targetMerchant, "SETTLED");
        assertThat(results).hasSize(2);

        List<SettlementTransaction> pendingResults = queryService.findByMerchantAndStatus(targetMerchant, "PENDING");
        assertThat(pendingResults).hasSize(1);
    }
}
