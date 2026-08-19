package com.finflow.chapter080.correct.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Documented
@Constraint(validatedBy = PaymentMethodDetailsValidator.class)
@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPaymentMethodDetails {
    String message() default "Invalid payment method details provided";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}
