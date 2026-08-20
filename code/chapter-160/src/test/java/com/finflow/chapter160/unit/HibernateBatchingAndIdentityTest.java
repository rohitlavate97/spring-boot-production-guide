package com.finflow.chapter160.unit;

import com.finflow.chapter160.Chapter160Application;
import com.finflow.chapter160.correct.ChunkedHibernateBatchService;
import com.finflow.chapter160.domain.IdentitySettlementRecord;
import com.finflow.chapter160.domain.SettlementStatus;
import com.finflow.chapter160.dto.SettlementIngestItem;
import com.finflow.chapter160.incorrect.NaiveBulkSaveServiceIncorrect;
import com.finflow.chapter160.repository.IdentitySettlementRepository;
import com.finflow.chapter160.repository.SequenceSettlementRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter160Application.class)
public class HibernateBatchingAndIdentityTest {

    @Autowired
    private IdentitySettlementRepository identityRepository;

    @Autowired
    private SequenceSettlementRepository sequenceRepository;

    @Autowired
    private ChunkedHibernateBatchService chunkedService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private NaiveBulkSaveServiceIncorrect incorrectService;

    @BeforeEach
    public void setup() {
        incorrectService = new NaiveBulkSaveServiceIncorrect(null, identityRepository);
        identityRepository.deleteAll();
        sequenceRepository.deleteAll();
    }

    private List<SettlementIngestItem> generateItems(int count) {
        List<SettlementIngestItem> items = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            items.add(new SettlementIngestItem(
                    "MERCH_" + i,
                    "TX_REF_" + i,
                    BigDecimal.valueOf(100.00),
                    BigDecimal.valueOf(2.50),
                    BigDecimal.valueOf(97.50),
                    "USD"
            ));
        }
        return items;
    }

    @Test
    public void testIdentityGenerator_disablesBatching_executesIndividualInserts() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<SettlementIngestItem> items = generateItems(50);

        transactionTemplate.executeWithoutResult(status -> {
            incorrectService.ingestWithIdentityDisablingBatching("BATCH_IDENTITY_1", items);
        });

        // With IDENTITY, Hibernate executes 50 individual insert statements without batching
        long insertCount = statistics.getEntityInsertCount();
        assertThat(insertCount).isEqualTo(50);
        assertThat(identityRepository.count()).isEqualTo(50);
    }

    @Test
    public void testSequenceGenerator_enablesBatching() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<SettlementIngestItem> items = generateItems(100);

        chunkedService.ingestWithSequenceBatching("BATCH_SEQ_1", items);

        // Sequence generator pre-allocates IDs and batches the INSERTs
        assertThat(sequenceRepository.count()).isEqualTo(100);
        assertThat(statistics.getEntityInsertCount()).isEqualTo(100);
    }
}
