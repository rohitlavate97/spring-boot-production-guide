package com.finflow.troubleshooting.module08.service;

import com.finflow.troubleshooting.module08.entity.CustomerEntity;
import com.finflow.troubleshooting.module08.repository.CustomerRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.LazyInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class CustomerBatchService {

    private static final Logger log = LoggerFactory.getLogger(CustomerBatchService.class);

    @PersistenceContext
    private EntityManager entityManager;

    private final CustomerRepository customerRepository;

    public CustomerBatchService(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    // Demonstrates L1 cache clearing to prevent OutOfMemoryError during bulk inserts
    @Transactional
    public void executeBatchInsertWithL1Clear(List<CustomerEntity> customers, int batchSize) {
        for (int i = 0; i < customers.size(); i++) {
            entityManager.persist(customers.get(i));
            if (i > 0 && i % batchSize == 0) {
                log.info("[BatchService] Flushing and clearing L1 persistence context at index {}", i);
                entityManager.flush();
                entityManager.clear(); // Prevents L1 cache bloat
            }
        }
        entityManager.flush();
        entityManager.clear();
    }

    // Demonstrates LazyInitializationException outside transaction when OSIV=false
    public int getCustomerOrderCountWithoutTransaction(Long customerId) {
        CustomerEntity customer = customerRepository.findById(customerId).orElseThrow();
        // Since OSIV is false and method has no @Transactional, accessing orders here throws LazyInitializationException
        return customer.getOrders().size();
    }

    // Fixed version: with @Transactional, the Hibernate session stays open during collection traversal
    @Transactional(readOnly = true)
    public int getCustomerOrderCountWithTransaction(Long customerId) {
        CustomerEntity customer = customerRepository.findById(customerId).orElseThrow();
        return customer.getOrders().size();
    }
}
