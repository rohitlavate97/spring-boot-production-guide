package com.finflow.chapter170.correct;

import com.finflow.chapter170.domain.AuditRecord;
import com.finflow.chapter170.repository.AuditRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuditLogServiceCorrect {

    private final AuditRecordRepository auditRecordRepository;

    public AuditLogServiceCorrect(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    /**
     * Executes in an independent physical database transaction via REQUIRES_NEW.
     * Commits independently even if the calling parent transaction rolls back.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAuditLog(String action, String referenceId, String status, String details) {
        AuditRecord audit = new AuditRecord(
                UUID.randomUUID(),
                action,
                referenceId,
                status,
                details,
                Instant.now()
        );
        auditRecordRepository.save(audit);
    }
}
