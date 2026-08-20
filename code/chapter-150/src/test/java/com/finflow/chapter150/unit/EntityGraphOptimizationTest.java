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
public class EntityGraphOptimizationTest {

    @Autowired
    private PaymentOrderRepository orderRepository;

    @Autowired
    private MerchantAccountRepository merchantRepository;

    @Autowired
    private OptimizedReconciliationService optimizedService;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private final String merchantId = "MERCHANT_ENTITY_GRAPH";

    @BeforeEach
    public void setup() {
        orderRepository.deleteAll();
        merchantRepository.deleteAll();

        MerchantAccount merchant = new MerchantAccount(
                UUID.randomUUID(),
                merchantId,
                "Entity Graph Systems",
                "TIER_PLATINUM"
        );
        merchantRepository.saveAndFlush(merchant);

        for (int i = 1; i <= 8; i++) {
            PaymentOrder order = new PaymentOrder(
                    UUID.randomUUID(),
                    "ORD-EG-" + i,
                    merchantId,
                    merchant,
                    BigDecimal.valueOf(120.00),
                    "USD",
                    PaymentStatus.AUTHORIZED,
                    Instant.now()
            );

            order.addItem(new PaymentItem(
                    UUID.randomUUID(),
                    order,
                    "SKU-EG-" + i,
                    "API Plan Subscription " + i,
                    BigDecimal.valueOf(120.00),
                    1,
                    BigDecimal.valueOf(2.40)
            ));

            orderRepository.save(order);
        }
        orderRepository.flush();
    }

    @Test
    public void testEntityGraph_executesSingleQueryForGraphPaths() {
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        List<PaymentOrderSummaryDto> result = optimizedService.getReconciliationReportViaEntityGraph(merchantId);

        assertThat(result).hasSize(8);
        assertThat(result.get(0).merchantBusinessName()).isEqualTo("Entity Graph Systems");
        assertThat(result.get(0).items()).hasSize(1);

        // Statement count is 1 because EntityGraph fetched merchantAccount and items in a single query
        long statementCount = statistics.getPrepareStatementCount();
        assertThat(statementCount).isEqualTo(1);
    }
}
