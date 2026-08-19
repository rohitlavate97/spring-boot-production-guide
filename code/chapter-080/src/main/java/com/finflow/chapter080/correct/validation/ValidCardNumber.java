package com.finflow.chapter080.correct.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = LuhnCardValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR, ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCardNumber {
    String message() default "Invalid card number (failed Luhn check)";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
