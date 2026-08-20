package com.finflow.chapter150.unit;

import com.finflow.chapter150.Chapter150Application;
import com.finflow.chapter150.domain.MerchantAccount;
import com.finflow.chapter150.domain.PaymentItem;
import com.finflow.chapter150.domain.PaymentOrder;
import com.finflow.chapter150.domain.PaymentStatus;
import com.finflow.chapter150.incorrect.NPlusOneReconciliationServiceIncorrect;
import com.finflow.chapter150.repository.MerchantAccountRepository;
import com.finflow.chapter150.repository.PaymentOrderRepository;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(classes = Chapter150Application.class)
public class NPlusOneAndLazyInitTest {

    @Autowired
    private PaymentOrderRepository orderRepository;

    @Autowired
    private MerchantAccountRepository merchantRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private NPlusOneReconciliationServiceIncorrect incorrectService;

    private final String merchantId = "MERCHANT_ACME_001";

    @BeforeEach
    public void setup() {
        incorrectService = new NPlusOneReconciliationServiceIncorrect(orderRepository);
        orderRepository.deleteAll();
        merchantRepository.deleteAll();

        MerchantAccount merchant = new MerchantAccount(
                UUID.randomUUID(),
                merchantId,
                "Acme Corp Payments",
                "ENTERPRISE_TIER_1"
        );
        merchantRepository.saveAndFlush(merchant);

        for (int i = 1; i <= 5; i++) {
            PaymentOrder order = new PaymentOrder(
                    UUID.randomUUID(),
                    "ORD-2026-" + i,
                    merchantId,
                    merchant,
                    BigDecimal.valueOf(100.00 * i),
                    "USD",
                    PaymentStatus.AUTHORIZED,
                    Instant.now()
            );

            order.addItem(new PaymentItem(
                    UUID.randomUUID(),
                    order,
                    "SKU-ITEM-" + i + "-A",
                    "Software Subscription Month " + i,
                    BigDecimal.valueOf(50.00),
                    1,
                    BigDecimal.valueOf(1.50)
            ));

            order.addItem(new PaymentItem(
                    UUID.randomUUID(),
                    order,
                    "SKU-ITEM-" + i + "-B",
                    "Add-on Storage Tier " + i,
                    BigDecimal.valueOf(50.00),
                    1,
                    BigDecimal.valueOf(1.50)
            ));

            orderRepository.save(order);
        }
        orderRepository.flush();
    }

    @Test
    public void testLazyInitializationException_whenAccessingOutsideTransaction() {
        // When spring.jpa.open-in-view=false, accessing lazy proxy outside @Transactional throws LazyInitializationException
        assertThatThrownBy(() -> incorrectService.getOrderSummaryWithoutTransaction(merchantId))
                .isInstanceOf(LazyInitializationException.class)
                .hasMessageContaining("could not initialize proxy");
    }

    @Test
    public void testIncorrectService_fetchesAllDataWithNPlusOne() {
        // Executed inside transaction: does not throw LazyInitializationException, but triggers N+1 query storm
        var report = transactionTemplate.execute(status -> incorrectService.getDailyReconciliationReport(merchantId));
        assertThat(report).isNotNull();
        assertThat(report).hasSize(5);
        assertThat(report.get(0).items()).hasSize(2);
        assertThat(report.get(0).merchantBusinessName()).isEqualTo("Acme Corp Payments");
    }
}
