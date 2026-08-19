package com.finflow.chapter110.correct.aspect;

import com.finflow.chapter110.domain.AuditRecord;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

@Aspect
@Component
@Order(10)
public class PaymentAuditAspect {

    private final Queue<AuditRecord> auditRecords = new ConcurrentLinkedQueue<>();

    @Around("@annotation(auditPayment)")
    public Object auditPaymentExecution(ProceedingJoinPoint joinPoint, AuditPayment auditPayment) throws Throwable {
        long start = System.currentTimeMillis();
        String paymentId = extractPaymentId(joinPoint);
        String status = "SUCCESS";
        try {
            return joinPoint.proceed();
        } catch (Throwable t) {
            status = "FAILED";
            throw t;
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            auditRecords.add(new AuditRecord(
                    paymentId,
                    auditPayment.action(),
                    status,
                    durationMs,
                    Instant.now()
            ));
        }
    }

    private String extractPaymentId(ProceedingJoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args != null && args.length > 0 && args[0] != null) {
            // Very simplified extraction for demo purposes
            if (args[0] instanceof com.finflow.chapter110.domain.PaymentExecutionRequest req) {
                return req.paymentId();
            } else if (args[0] instanceof String str) {
                return str;
            }
        }
        return "UNKNOWN";
    }

    public Queue<AuditRecord> getAuditRecords() {
        return auditRecords;
    }
    
    public void clear() {
        auditRecords.clear();
    }
}
