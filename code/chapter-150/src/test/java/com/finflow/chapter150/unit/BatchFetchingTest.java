package com.finflow.chapter150.unit;

import com.finflow.chapter150.Chapter150Application;
import com.finflow.chapter150.correct.OptimizedReconciliationService;
import com.finflow.chapter150.domain.MerchantAccount;
import com.finflow.chapter150.domain.PaymentItem;
import com.finflow.chapter150.domain.PaymentOrder;
import com.finflow.chapter150.domain.PaymentStatus;
import com.finflow.chapter150.dto.PaymentOrderSummaryDto;
import com.finflow.chapter150.repository.MerchantAccountRepository;
import com.finflow.chapter150.repository.PaymentOrderRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter150Application.class)
public class BatchFetchingTest {

    @Autowired
    private PaymentOrderRepository orderRepository;

    @Autowired
    private MerchantAccountRepository merchantRepository;

    @Autowired
    private OptimizedReconciliationService optimizedService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final String merchantId = "MERCHANT_BATCH_FETCH";

    @BeforeEach
    public void setup() {
        orderRepository.deleteAll();
        merchantRepository.deleteAll();

        MerchantAccount merchant = new MerchantAccount(
                UUID.randomUUID(),
                merchantId,
                "Batch Fetch Corp",
                "ENTERPRISE_TIER_2"
        );
        merchantRepository.saveAndFlush(merchant);

        for (int i = 1; i <= 10; i++) {
            PaymentOrder order = new PaymentOrder(
                    UUID.randomUUID(),
                    "ORD-BATCH-" + i,
                    merchantId,
                    merchant,
                    BigDecimal.valueOf(80.00),
                    "USD",
                    PaymentStatus.AUTHORIZED,
                    Instant.now()
            );

            order.addItem(new PaymentItem(
                    UUID.randomUUID(),
                    order,
                    "SKU-BATCH-" + i,
                    "Batch processing unit " + i,
                    BigDecimal.valueOf(80.00),
                    1,
                    BigDecimal.valueOf(1.60)
            ));

            orderRepository.save(order);
        }
        orderRepository.flush();
    }

    @Test
    public void testBatchFetching_reducesQueryStormToBatchedInQueries() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        // Invokes findAllByMerchantId without JOIN FETCH, relying on @BatchSize(size = 50)
        List<PaymentOrderSummaryDto> result = optimizedService.getReconciliationReportViaBatchFetching(merchantId);

        assertThat(result).hasSize(10);
        assertThat(result.get(0).merchantBusinessName()).isEqualTo("Batch Fetch Corp");
        assertThat(result.get(0).items()).hasSize(1);

        // Instead of 1 + 10 (merchant) + 10 (items) = 21 queries,
        // batch fetching executes only 3 queries (or 2 if merchant proxy was resolved via L1 cache / single batch)
        long statementCount = statistics.getPrepareStatementCount();
        assertThat(statementCount).isLessThanOrEqualTo(3);
    }
}
