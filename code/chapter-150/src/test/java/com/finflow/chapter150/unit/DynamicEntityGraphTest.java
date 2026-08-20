package com.finflow.chapter150.unit;

import com.finflow.chapter150.Chapter150Application;
import com.finflow.chapter150.correct.DynamicEntityGraphService;
import com.finflow.chapter150.domain.MerchantAccount;
import com.finflow.chapter150.domain.PaymentItem;
import com.finflow.chapter150.domain.PaymentOrder;
import com.finflow.chapter150.domain.PaymentStatus;
import com.finflow.chapter150.repository.MerchantAccountRepository;
import com.finflow.chapter150.repository.PaymentOrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter150Application.class)
public class DynamicEntityGraphTest {

    @Autowired
    private PaymentOrderRepository orderRepository;

    @Autowired
    private MerchantAccountRepository merchantRepository;

    @Autowired
    private DynamicEntityGraphService dynamicGraphService;

    private final String merchantId = "MERCHANT_DYNAMIC_GRAPH";

    @BeforeEach
    public void setup() {
        orderRepository.deleteAll();
        merchantRepository.deleteAll();

        MerchantAccount merchant = new MerchantAccount(
                UUID.randomUUID(),
                merchantId,
                "Dynamic Graph Systems",
                "ENTERPRISE_CUSTOM"
        );
        merchantRepository.saveAndFlush(merchant);

        PaymentOrder order = new PaymentOrder(
                UUID.randomUUID(),
                "ORD-DYNAMIC-1",
                merchantId,
                merchant,
                BigDecimal.valueOf(999.99),
                "USD",
                PaymentStatus.AUTHORIZED,
                Instant.now()
        );

        order.addItem(new PaymentItem(
                UUID.randomUUID(),
                order,
                "SKU-DYN-01",
                "Dynamic Service Item",
                BigDecimal.valueOf(999.99),
                1,
                BigDecimal.valueOf(19.99)
        ));

        orderRepository.saveAndFlush(order);
    }

    @Test
    public void testDynamicGraph_withMerchantAndItemsExpansion() {
        List<PaymentOrder> orders = dynamicGraphService.findOrdersWithDynamicGraph(
                merchantId,
                Set.of("merchant", "items")
        );

        assertThat(orders).hasSize(1);
        PaymentOrder order = orders.get(0);
        assertThat(order.getMerchantAccount().getBusinessName()).isEqualTo("Dynamic Graph Systems");
        assertThat(order.getItems()).hasSize(1);
    }
}
