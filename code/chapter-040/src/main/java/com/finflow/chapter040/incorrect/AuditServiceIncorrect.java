package com.finflow.chapter040.incorrect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditServiceIncorrect {
    private static final Logger log = LoggerFactory.getLogger(AuditServiceIncorrect.class);

    @Autowired
    private AuditContextIncorrect auditContext;

    @Async
    public void writeAuditLog(String action) {
        // BUG: This will fail because the request scope is not active on this background thread!
        try {
            log.info("Async Audit - User: {}, Action: {}, RequestId: {}", 
                auditContext.getUserId(), action, auditContext.getRequestId());
        } catch (IllegalStateException e) {
            log.error("Failed to access audit context in async thread: {}", e.getMessage());
            throw e;
        }
    }
}
