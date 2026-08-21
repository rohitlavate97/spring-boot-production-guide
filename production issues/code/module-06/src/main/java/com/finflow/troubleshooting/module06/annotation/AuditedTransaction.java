package com.finflow.troubleshooting.module06.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditedTransaction {
    String action() default "DEBIT_TRANSACTION";
}
