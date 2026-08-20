package com.finflow.chapter160.unit;

import com.finflow.chapter160.Chapter160Application;
import com.finflow.chapter160.correct.ChunkedHibernateBatchService;
import com.finflow.chapter160.domain.SettlementRecord;
import com.finflow.chapter160.domain.SettlementStatus;
import com.finflow.chapter160.dto.SettlementIngestItem;
import com.finflow.chapter160.repository.SettlementRecordRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter160Application.class)
public class ChunkedBatchMemoryTest {

    @Autowired
    private SettlementRecordRepository settlementRecordRepository;

    @Autowired
    private ChunkedHibernateBatchService chunkedService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final String batchId = "BATCH_CHUNK_150";

    @BeforeEach
    public void setup() {
        settlementRecordRepository.deleteAll();
    }

    private List<SettlementIngestItem> generateItems(int count) {
        List<SettlementIngestItem> items = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            items.add(new SettlementIngestItem(
                    "MERCH_" + (i % 10),
                    "TX_REF_CHUNK_" + i,
                    BigDecimal.valueOf(150.00),
                    BigDecimal.valueOf(3.00),
                    BigDecimal.valueOf(147.00),
                    "USD"
            ));
        }
        return items;
    }

    @Test
    public void testChunkedFlushClear_ingestsAllRecordsSuccessfully() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<SettlementIngestItem> items = generateItems(150);

        chunkedService.ingestWithChunkedFlushClear(batchId, items);

        List<SettlementRecord> savedRecords = settlementRecordRepository.findAllByBatchId(batchId);
        assertThat(savedRecords).hasSize(150);
        assertThat(savedRecords.get(0).getStatus()).isEqualTo(SettlementStatus.PENDING);
        assertThat(statistics.getEntityInsertCount()).isEqualTo(150);
    }
}
