package com.finflow.troubleshooting.module27.repository;

import com.finflow.troubleshooting.module27.model.SagaInstance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SagaInstanceRepository extends JpaRepository<SagaInstance, String> {
    List<SagaInstance> findByStatus(SagaInstance.SagaStatus status);
}
