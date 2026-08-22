package com.finflow.troubleshooting.module27.repository;

import com.finflow.troubleshooting.module27.model.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<OrderEntity, String> {
}
