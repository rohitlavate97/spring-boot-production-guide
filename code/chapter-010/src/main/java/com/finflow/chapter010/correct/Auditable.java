package com.finflow.chapter010.correct;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)  // FIXED: visible at runtime
@Target(ElementType.METHOD)
@Documented
public @interface Auditable {
    String action();
    String resourceType();
}
