package com.finflow.troubleshooting.module08;

import com.finflow.troubleshooting.module08.entity.CustomerEntity;
import com.finflow.troubleshooting.module08.entity.OrderEntity;
import com.finflow.troubleshooting.module08.repository.CustomerRepository;
import com.finflow.troubleshooting.module08.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = Module08Application.class)
@AutoConfigureMockMvc
public class Module08IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();
        CustomerEntity c = new CustomerEntity("Elena", "elena@example.com");
        c.addOrder(new OrderEntity("ORD-E1", new BigDecimal("500.00"), c));
        customerRepository.save(c);
    }

    @Test
    void testGetCustomersWithJoinFetchEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/customers/join-fetch"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Elena"))
                .andExpect(jsonPath("$[0].orders[0].orderNumber").value("ORD-E1"));
    }

    @Test
    void testGetCustomersWithEntityGraphEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/customers/entity-graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Elena"))
                .andExpect(jsonPath("$[0].orders[0].orderNumber").value("ORD-E1"));
    }
}
