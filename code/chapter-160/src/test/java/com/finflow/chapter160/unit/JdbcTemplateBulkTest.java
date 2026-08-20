package com.finflow.chapter160.unit;

import com.finflow.chapter160.Chapter160Application;
import com.finflow.chapter160.correct.JdbcTemplateBulkService;
import com.finflow.chapter160.domain.SettlementRecord;
import com.finflow.chapter160.domain.SettlementStatus;
import com.finflow.chapter160.dto.SettlementIngestItem;
import com.finflow.chapter160.repository.SettlementRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter160Application.class)
public class JdbcTemplateBulkTest {

    @Autowired
    private SettlementRecordRepository settlementRecordRepository;

    @Autowired
    private JdbcTemplateBulkService jdbcTemplateBulkService;

    private final String batchId = "BATCH_JDBC_100";

    @BeforeEach
    public void setup() {
        settlementRecordRepository.deleteAll();
    }

    @Test
    public void testJdbcTemplateBulkIngest_writes100RecordsDirectly() {
        List<SettlementIngestItem> items = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            items.add(new SettlementIngestItem(
                    "MERCH_JDBC_" + i,
                    "TX_JDBC_" + i,
                    BigDecimal.valueOf(500.00),
                    BigDecimal.valueOf(10.00),
                    BigDecimal.valueOf(490.00),
                    "USD"
            ));
        }

        jdbcTemplateBulkService.ingestBulkViaJdbcTemplate(batchId, items);

        List<SettlementRecord> records = settlementRecordRepository.findAllByBatchId(batchId);
        assertThat(records).hasSize(100);
        assertThat(records.get(0).getStatus()).isEqualTo(SettlementStatus.PENDING);
    }
}
