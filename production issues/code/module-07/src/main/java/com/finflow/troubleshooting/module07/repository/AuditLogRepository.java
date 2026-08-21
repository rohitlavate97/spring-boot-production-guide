package com.finflow.troubleshooting.module07.repository;

import com.finflow.troubleshooting.module07.entity.AuditLogEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditLogRepository extends JpaRepository<AuditLogEntity, Long> {
}
