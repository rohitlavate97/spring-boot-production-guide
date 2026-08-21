package com.finflow.troubleshooting.module08;

import com.finflow.troubleshooting.module08.entity.CustomerEntity;
import com.finflow.troubleshooting.module08.entity.OrderEntity;
import com.finflow.troubleshooting.module08.repository.CustomerRepository;
import com.finflow.troubleshooting.module08.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module08Application.class)
public class NPlusOneDetectionTest {

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();

        CustomerEntity c1 = new CustomerEntity("Alice", "alice@example.com");
        c1.addOrder(new OrderEntity("ORD-A1", new BigDecimal("100.00"), c1));
        c1.addOrder(new OrderEntity("ORD-A2", new BigDecimal("50.00"), c1));

        CustomerEntity c2 = new CustomerEntity("Bob", "bob@example.com");
        c2.addOrder(new OrderEntity("ORD-B1", new BigDecimal("200.00"), c2));

        CustomerEntity c3 = new CustomerEntity("Charlie", "charlie@example.com");
        c3.addOrder(new OrderEntity("ORD-C1", new BigDecimal("300.00"), c3));

        customerRepository.saveAll(List.of(c1, c2, c3));
    }

    @Test
    @Transactional(readOnly = true)
    void testJoinFetchLoadsAllOrdersInSingleQuery() {
        List<CustomerEntity> rawList = customerRepository.findAllWithJoinFetch();
        Set<CustomerEntity> uniqueCustomers = new LinkedHashSet<>(rawList);
        assertThat(uniqueCustomers).hasSize(3);

        int totalOrders = uniqueCustomers.stream().mapToInt(c -> c.getOrders().size()).sum();
        assertThat(totalOrders).isEqualTo(4);
    }

    @Test
    @Transactional(readOnly = true)
    void testEntityGraphLoadsAllOrdersInSingleQuery() {
        List<CustomerEntity> rawList = customerRepository.findAllWithEntityGraph();
        Set<CustomerEntity> uniqueCustomers = new LinkedHashSet<>(rawList);
        assertThat(uniqueCustomers).hasSize(3);

        int totalOrders = uniqueCustomers.stream().mapToInt(c -> c.getOrders().size()).sum();
        assertThat(totalOrders).isEqualTo(4);
    }
}
