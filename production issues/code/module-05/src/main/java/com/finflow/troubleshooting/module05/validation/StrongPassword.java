package com.finflow.troubleshooting.module05.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {
    String message() default "Password must be at least 8 characters, contain at least 1 digit, 1 uppercase letter, and 1 special symbol";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
