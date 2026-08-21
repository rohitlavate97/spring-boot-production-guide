package com.finflow.troubleshooting.module08.repository;

import com.finflow.troubleshooting.module08.entity.CustomerEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CustomerRepository extends JpaRepository<CustomerEntity, Long> {

    // 1. Standard findAll -> causes N+1 queries when traversing customer.getOrders()
    List<CustomerEntity> findAll();

    // 2. Solution A: Explicit JPQL JOIN FETCH
    @Query("SELECT DISTINCT c FROM CustomerEntity c LEFT JOIN FETCH c.orders")
    List<CustomerEntity> findAllWithJoinFetch();

    // 3. Solution B: Spring Data @EntityGraph
    @EntityGraph(attributePaths = {"orders"})
    @Query("SELECT DISTINCT c FROM CustomerEntity c")
    List<CustomerEntity> findAllWithEntityGraph();
}
