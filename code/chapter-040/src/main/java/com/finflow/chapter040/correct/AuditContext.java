package com.finflow.chapter040.correct;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

@Component("auditContext")
@RequestScope
public class AuditContext {
    private String userId;
    private String requestId;
    private String remoteAddr;

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getRequestId() { return requestId; }
    public void setRequestId(String requestId) { this.requestId = requestId; }

    public String getRemoteAddr() { return remoteAddr; }
    public void setRemoteAddr(String remoteAddr) { this.remoteAddr = remoteAddr; }
}
