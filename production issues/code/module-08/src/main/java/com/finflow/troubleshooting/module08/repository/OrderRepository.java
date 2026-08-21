package com.finflow.troubleshooting.module08.repository;

import com.finflow.troubleshooting.module08.entity.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {
}
