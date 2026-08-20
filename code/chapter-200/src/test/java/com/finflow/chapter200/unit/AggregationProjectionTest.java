package com.finflow.chapter200.unit;

import com.finflow.chapter200.Chapter200Application;
import com.finflow.chapter200.correct.SettlementQueryServiceCorrect;
import com.finflow.chapter200.domain.SettlementTransaction;
import com.finflow.chapter200.dto.SettlementSummaryDto;
import com.finflow.chapter200.repository.SettlementTransactionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter200Application.class)
public class AggregationProjectionTest {

    @Autowired
    private SettlementTransactionRepository repository;

    @Autowired
    private SettlementQueryServiceCorrect queryService;

    private final String merchantId = "MERCHANT_SUMMARY_001";

    @BeforeEach
    public void setup() {
        repository.deleteAll();

        repository.save(new SettlementTransaction(
                UUID.randomUUID(), merchantId, "SETTLED", BigDecimal.valueOf(100.00), "USD", "GTW-1", Instant.now(), null
        ));
        repository.save(new SettlementTransaction(
                UUID.randomUUID(), merchantId, "SETTLED", BigDecimal.valueOf(250.50), "USD", "GTW-2", Instant.now(), null
        ));
        repository.save(new SettlementTransaction(
                UUID.randomUUID(), merchantId, "SETTLED", BigDecimal.valueOf(49.50), "USD", "GTW-3", Instant.now(), null
        ));
        repository.flush();
    }

    @Test
    public void testSummaryAggregation_returnsAccurateSumAndCount() {
        Optional<SettlementSummaryDto> summary = queryService.getSettlementSummary(merchantId, "SETTLED");

        assertThat(summary).isPresent();
        assertThat(summary.get().merchantId()).isEqualTo(merchantId);
        assertThat(summary.get().status()).isEqualTo("SETTLED");
        assertThat(summary.get().transactionCount()).isEqualTo(3);
        assertThat(summary.get().totalAmount()).isEqualByComparingTo(BigDecimal.valueOf(400.00));
    }
}
