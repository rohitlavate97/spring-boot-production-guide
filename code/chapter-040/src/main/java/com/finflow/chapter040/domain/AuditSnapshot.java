package com.finflow.chapter040.domain;

import com.finflow.chapter040.correct.AuditContext;
import java.time.Instant;

public record AuditSnapshot(String userId, String requestId, String remoteAddr, Instant capturedAt) {
    public static AuditSnapshot capture(AuditContext context) {
        return new AuditSnapshot(
            context.getUserId(),
            context.getRequestId(),
            context.getRemoteAddr(),
            Instant.now()
        );
    }
}
