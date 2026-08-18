package com.finflow.chapter010.incorrect;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.CLASS)  // BUG: invisible at runtime
@Target(ElementType.METHOD)
public @interface AuditableIncorrect {
    String action();
    String resourceType();
}
