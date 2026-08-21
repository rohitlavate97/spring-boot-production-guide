package com.finflow.troubleshooting.module08;

import com.finflow.troubleshooting.module08.entity.CustomerEntity;
import com.finflow.troubleshooting.module08.entity.OrderEntity;
import com.finflow.troubleshooting.module08.repository.CustomerRepository;
import com.finflow.troubleshooting.module08.repository.OrderRepository;
import com.finflow.troubleshooting.module08.service.CustomerBatchService;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = Module08Application.class)
public class LazyInitializationExceptionTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CustomerBatchService batchService;

    private Long customerId;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        CustomerEntity c = new CustomerEntity("David", "david@example.com");
        c.addOrder(new OrderEntity("ORD-D1", new BigDecimal("150.00"), c));
        c.addOrder(new OrderEntity("ORD-D2", new BigDecimal("75.00"), c));
        CustomerEntity saved = customerRepository.save(c);
        this.customerId = saved.getId();
    }

    @Test
    void testLazyInitializationExceptionOccursOutsideTransactionWhenOsivIsDisabled() {
        assertThrows(LazyInitializationException.class, () ->
                batchService.getCustomerOrderCountWithoutTransaction(customerId));
    }

    @Test
    void testTransactionalMethodKeepsSessionOpenAndSucceeds() {
        int orderCount = batchService.getCustomerOrderCountWithTransaction(customerId);
        assertThat(orderCount).isEqualTo(2);
    }
}
