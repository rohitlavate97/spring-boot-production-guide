package com.finflow.troubleshooting.module08;

import com.finflow.troubleshooting.module08.entity.CustomerEntity;
import com.finflow.troubleshooting.module08.repository.CustomerRepository;
import com.finflow.troubleshooting.module08.repository.OrderRepository;
import com.finflow.troubleshooting.module08.service.CustomerBatchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module08Application.class)
public class BatchProcessingL1ClearTest {

    @Autowired
    private CustomerBatchService batchService;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Test
    void testBatchInsertWithL1ClearPersistsAllEntitiesSuccessfully() {
        orderRepository.deleteAllInBatch();
        customerRepository.deleteAllInBatch();

        List<CustomerEntity> bulkList = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            bulkList.add(new CustomerEntity("BulkUser" + i, "bulk" + i + "@example.com"));
        }

        // Process in batches of 10 with entityManager.flush() + clear()
        batchService.executeBatchInsertWithL1Clear(bulkList, 10);

        assertThat(customerRepository.count()).isEqualTo(25);
    }
}
