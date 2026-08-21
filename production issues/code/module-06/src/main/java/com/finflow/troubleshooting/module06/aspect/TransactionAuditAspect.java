package com.finflow.troubleshooting.module06.aspect;

import com.finflow.troubleshooting.module06.annotation.AuditedTransaction;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Aspect
@Component
@Order(1)
public class TransactionAuditAspect {

    private static final Logger log = LoggerFactory.getLogger(TransactionAuditAspect.class);
    private final AtomicInteger interceptionCount = new AtomicInteger(0);

    @Around("@annotation(auditedTransaction)")
    public Object interceptAuditedMethod(ProceedingJoinPoint joinPoint, AuditedTransaction auditedTransaction) throws Throwable {
        interceptionCount.incrementAndGet();
        log.info("[TransactionAuditAspect] INTERCEPTED method '{}' with action='{}'",
                joinPoint.getSignature().toShortString(), auditedTransaction.action());

        long start = System.currentTimeMillis();
        try {
            return joinPoint.proceed();
        } finally {
            long duration = System.currentTimeMillis() - start;
            log.info("[TransactionAuditAspect] COMPLETED method '{}' in {}ms",
                    joinPoint.getSignature().toShortString(), duration);
        }
    }

    public int getInterceptionCount() {
        return interceptionCount.get();
    }

    public void resetCount() {
        interceptionCount.set(0);
    }
}
