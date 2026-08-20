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
public class JoinFetchOptimizationTest {

    @Autowired
    private PaymentOrderRepository orderRepository;

    @Autowired
    private MerchantAccountRepository merchantRepository;

    @Autowired
    private OptimizedReconciliationService optimizedService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final String merchantId = "MERCHANT_JOIN_FETCH";

    @BeforeEach
    public void setup() {
        orderRepository.deleteAll();
        merchantRepository.deleteAll();

        MerchantAccount merchant = new MerchantAccount(
                UUID.randomUUID(),
                merchantId,
                "Join Fetch Enterprise",
                "TIER_GOLD"
        );
        merchantRepository.saveAndFlush(merchant);

        for (int i = 1; i <= 10; i++) {
            PaymentOrder order = new PaymentOrder(
                    UUID.randomUUID(),
                    "ORD-JF-" + i,
                    merchantId,
                    merchant,
                    BigDecimal.valueOf(250.00),
                    "USD",
                    PaymentStatus.SETTLED,
                    Instant.now()
            );

            order.addItem(new PaymentItem(
                    UUID.randomUUID(),
                    order,
                    "SKU-ITEM-" + i,
                    "Cloud Gateway Service " + i,
                    BigDecimal.valueOf(250.00),
                    1,
                    BigDecimal.valueOf(5.00)
            ));

            orderRepository.save(order);
        }
        orderRepository.flush();
    }

    @Test
    public void testJoinFetch_executesSingleQueryForOrdersItemsAndMerchant() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<PaymentOrderSummaryDto> result = optimizedService.getReconciliationReportViaJoinFetch(merchantId);

        // Verify data accuracy
        assertThat(result).hasSize(10);
        assertThat(result.get(0).merchantBusinessName()).isEqualTo("Join Fetch Enterprise");
        assertThat(result.get(0).items()).hasSize(1);

        // Verify statement count: Exactly 1 SQL query executed!
        long statementCount = statistics.getPrepareStatementCount();
        assertThat(statementCount).isEqualTo(1);
    }
}
