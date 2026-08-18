package com.finflow.chapter010.incorrect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Aspect
@Component
public class AuditAspectIncorrect {

    private static final Logger log = LoggerFactory.getLogger(AuditAspectIncorrect.class);

    @Around("@annotation(com.finflow.chapter010.incorrect.AuditableIncorrect)")
    public Object auditMethod(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // This will return null because retention policy is CLASS
        AuditableIncorrect annotation = method.getAnnotation(AuditableIncorrect.class);

        if (annotation == null) {
            log.debug("Annotation not found on method: {}. Proceeding without audit.", method.getName());
        } else {
            log.info("Auditing action: {} on resource: {}", annotation.action(), annotation.resourceType());
            // In a real scenario, would save AuditEntry here
        }

        return joinPoint.proceed();
    }
}
