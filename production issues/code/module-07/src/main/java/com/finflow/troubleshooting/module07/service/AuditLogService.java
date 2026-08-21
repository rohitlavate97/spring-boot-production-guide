package com.finflow.troubleshooting.module07.service;

import com.finflow.troubleshooting.module07.entity.AuditLogEntity;
import com.finflow.troubleshooting.module07.repository.AuditLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    // REQUIRES_NEW creates an independent transaction that commits even if the outer transaction rolls back
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuditLog(String operation, String details) {
        auditLogRepository.save(new AuditLogEntity(operation, details));
    }
}
