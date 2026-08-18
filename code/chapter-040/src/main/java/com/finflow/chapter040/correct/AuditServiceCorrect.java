package com.finflow.chapter040.correct;

import com.finflow.chapter040.domain.AuditSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
public class AuditServiceCorrect {
    private static final Logger log = LoggerFactory.getLogger(AuditServiceCorrect.class);

    @Autowired
    private AuditContext auditContext;

    public void handlePayment() {
        // Snapshot context on the HTTP thread BEFORE switching to the async thread
        AuditSnapshot snapshot = AuditSnapshot.capture(auditContext);
        writeAuditLogAsync(snapshot, "PAYMENT_PROCESSED");
    }

    @Async
    public void writeAuditLogAsync(AuditSnapshot snapshot, String action) {
        // Safe to use the snapshot across threads
        log.info("Async Audit - User: {}, Action: {}, RequestId: {}, Remote: {}", 
            snapshot.userId(), action, snapshot.requestId(), snapshot.remoteAddr());
    }
}
