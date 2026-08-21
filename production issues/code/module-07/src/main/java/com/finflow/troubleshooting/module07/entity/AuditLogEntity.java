package com.finflow.troubleshooting.module07.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "finflow_audit_logs")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String operation;

    @Column(nullable = false)
    private String details;

    @Column(nullable = false)
    private Instant createdAt;

    public AuditLogEntity() {}

    public AuditLogEntity(String operation, String details) {
        this.operation = operation;
        this.details = details;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getOperation() { return operation; }
    public String getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }
}
