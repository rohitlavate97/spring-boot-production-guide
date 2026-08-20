package com.finflow.chapter160.unit;

import com.finflow.chapter160.Chapter160Application;
import com.finflow.chapter160.correct.StatelessSessionBulkService;
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
public class StatelessSessionBulkTest {

    @Autowired
    private SettlementRecordRepository settlementRecordRepository;

    @Autowired
    private StatelessSessionBulkService statelessSessionBulkService;

    private final String batchId = "BATCH_STATELESS_100";

    @BeforeEach
    public void setup() {
        settlementRecordRepository.deleteAll();
    }

    @Test
    public void testStatelessSessionBulkIngest_writes100RecordsWithoutL1Cache() {
        List<SettlementIngestItem> items = new ArrayList<>();
        for (int i = 1; i <= 100; i++) {
            items.add(new SettlementIngestItem(
                    "MERCH_STATELESS_" + i,
                    "TX_STATELESS_" + i,
                    BigDecimal.valueOf(350.00),
                    BigDecimal.valueOf(7.00),
                    BigDecimal.valueOf(343.00),
                    "USD"
            ));
        }

        statelessSessionBulkService.ingestViaStatelessSession(batchId, items);

        List<SettlementRecord> records = settlementRecordRepository.findAllByBatchId(batchId);
        assertThat(records).hasSize(100);
        assertThat(records.get(0).getStatus()).isEqualTo(SettlementStatus.PENDING);
    }
}
