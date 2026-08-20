package com.finflow.chapter170.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "audit_records")
public class AuditRecord {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "action", nullable = false, length = 64)
    private String action;

    @Column(name = "reference_id", nullable = false, length = 64)
    private String referenceId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "details", length = 500)
    private String details;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AuditRecord() {
        // JPA requirement
    }

    public AuditRecord(UUID id, String action, String referenceId, String status, String details, Instant createdAt) {
        this.id = id;
        this.action = action;
        this.referenceId = referenceId;
        this.status = status;
        this.details = details;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getAction() { return action; }
    public String getReferenceId() { return referenceId; }
    public String getStatus() { return status; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AuditRecord that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
