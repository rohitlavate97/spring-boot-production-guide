package com.finflow.troubleshooting.module05.controller;

import com.finflow.troubleshooting.module05.exception.ResourceConflictException;
import com.finflow.troubleshooting.module05.model.CreateOrderRequest;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orders")
public class OrderValidationController {

    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(@Valid @RequestBody CreateOrderRequest request) {
        if ("DUPLICATE_USER".equalsIgnoreCase(request.customerId())) {
            throw new ResourceConflictException("An active order already exists for customer: " + request.customerId());
        }

        if ("DB_COLLISION".equalsIgnoreCase(request.customerId())) {
            // Simulates underlying database unique key constraint collision
            throw new DataIntegrityViolationException("ERROR: duplicate key value violates unique constraint 'uk_orders_reference_id' ON TABLE finflow_orders_tbl");
        }

        String orderId = "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        return ResponseEntity.ok(Map.of(
                "orderId", orderId,
                "customerId", request.customerId(),
                "itemCount", request.items().size(),
                "status", "CREATED"
        ));
    }
}
