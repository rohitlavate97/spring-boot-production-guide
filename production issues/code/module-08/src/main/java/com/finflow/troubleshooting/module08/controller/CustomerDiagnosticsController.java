package com.finflow.troubleshooting.module08.controller;

import com.finflow.troubleshooting.module08.entity.CustomerEntity;
import com.finflow.troubleshooting.module08.repository.CustomerRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
public class CustomerDiagnosticsController {

    private final CustomerRepository customerRepository;

    public CustomerDiagnosticsController(CustomerRepository customerRepository) {
        this.customerRepository = customerRepository;
    }

    @GetMapping("/join-fetch")
    public ResponseEntity<List<CustomerEntity>> getCustomersWithJoinFetch() {
        List<CustomerEntity> list = customerRepository.findAllWithJoinFetch();
        return ResponseEntity.ok(new ArrayList<>(new LinkedHashSet<>(list)));
    }

    @GetMapping("/entity-graph")
    public ResponseEntity<List<CustomerEntity>> getCustomersWithEntityGraph() {
        List<CustomerEntity> list = customerRepository.findAllWithEntityGraph();
        return ResponseEntity.ok(new ArrayList<>(new LinkedHashSet<>(list)));
    }

    @GetMapping("/count")
    public ResponseEntity<Map<String, Object>> getCustomerCount() {
        return ResponseEntity.ok(Map.of("totalCustomers", customerRepository.count()));
    }
}
