package com.finflow.chapter040.unit;

import com.finflow.chapter040.correct.AuditContext;
import com.finflow.chapter040.correct.AuditServiceCorrect;
import com.finflow.chapter040.domain.AuditSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequestScopeViolationTest {

    @Test
    void auditSnapshotCapturesStateSafely() {
        // Arrange
        AuditContext mockContext = new AuditContext();
        mockContext.setUserId("user-123");
        mockContext.setRequestId("req-999");
        mockContext.setRemoteAddr("192.168.1.1");

        // Act
        AuditSnapshot snapshot = AuditSnapshot.capture(mockContext);

        // Assert
        assertEquals("user-123", snapshot.userId());
        assertEquals("req-999", snapshot.requestId());
        assertEquals("192.168.1.1", snapshot.remoteAddr());
        // In reality, this snapshot gets passed to the async method safely 
        // without risking IllegalStateException from accessing the proxy off the HTTP thread.
    }
}
