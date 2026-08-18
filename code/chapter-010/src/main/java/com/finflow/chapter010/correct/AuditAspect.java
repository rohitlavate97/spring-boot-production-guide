package com.finflow.chapter010.correct;

import com.finflow.chapter010.domain.AuditEntry;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Aspect
@Component
public class AuditAspect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspect.class);
    private final ConcurrentMap<Method, Optional<Auditable>> annotationCache = new ConcurrentHashMap<>();
    
    private final AuditEntryRepository auditEntryRepository;

    public AuditAspect(AuditEntryRepository auditEntryRepository) {
        this.auditEntryRepository = auditEntryRepository;
    }

    @Around("@annotation(com.finflow.chapter010.correct.Auditable)")
    public Object auditMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Use thread-safe caching to avoid reflection overhead on every call
        Optional<Auditable> optionalAnnotation = annotationCache.computeIfAbsent(method, 
                m -> Optional.ofNullable(m.getAnnotation(Auditable.class)));

        if (optionalAnnotation.isPresent()) {
            Auditable annotation = optionalAnnotation.get();
            log.info("Auditing action: {} on resource: {}", annotation.action(), annotation.resourceType());
            
            AuditEntry entry = new AuditEntry();
            entry.setAction(annotation.action());
            entry.setResourceType(annotation.resourceType());
            // In a real app, resourceId would be extracted from arguments or return value
            entry.setResourceId("UNKNOWN");
            entry.setPerformedBy("SYSTEM"); // Would come from SecurityContext
            entry.setPerformedAt(Instant.now());
            entry.setDetails(String.format("Method %s called", method.getName()));
            
            auditEntryRepository.save(entry);
        } else {
            log.warn("Annotation not found on method: {}, although matched by Pointcut", method.getName());
        }

        return joinPoint.proceed();
    }
}
