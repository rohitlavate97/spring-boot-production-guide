package com.finflow.chapter160.unit;

import com.finflow.chapter160.Chapter160Application;
import com.finflow.chapter160.correct.ChunkedHibernateBatchService;
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
public class BulkUpdateTest {

    @Autowired
    private SettlementRecordRepository settlementRecordRepository;

    @Autowired
    private ChunkedHibernateBatchService chunkedService;

    private final String batchId = "BATCH_BULK_UPDATE_50";

    @BeforeEach
    public void setup() {
        settlementRecordRepository.deleteAll();

        List<SettlementIngestItem> items = new ArrayList<>();
        for (int i = 1; i <= 50; i++) {
            items.add(new SettlementIngestItem(
                    "MERCH_BULK_" + i,
                    "TX_BULK_" + i,
                    BigDecimal.valueOf(200.00),
                    BigDecimal.valueOf(4.00),
                    BigDecimal.valueOf(196.00),
                    "USD"
            ));
        }

        chunkedService.ingestWithChunkedFlushClear(batchId, items);
    }

    @Test
    public void testBulkUpdateStatus_executesSingleStatementAndUpdateAllRecords() {
        int updatedCount = chunkedService.bulkUpdateStatus(
                batchId,
                SettlementStatus.PENDING,
                SettlementStatus.PROCESSED
        );

        assertThat(updatedCount).isEqualTo(50);

        List<SettlementRecord> records = settlementRecordRepository.findAllByBatchId(batchId);
        assertThat(records).hasSize(50);
        assertThat(records).allMatch(r -> r.getStatus() == SettlementStatus.PROCESSED);
    }
}
