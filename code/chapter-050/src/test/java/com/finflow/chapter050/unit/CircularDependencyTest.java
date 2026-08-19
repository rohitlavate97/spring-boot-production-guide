package com.finflow.chapter050.unit;

import com.finflow.chapter050.correct.PaymentValidationServiceCorrect;
import com.finflow.chapter050.correct.RefundServiceCorrect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class CircularDependencyTest {

    @Autowired
    private RefundServiceCorrect refundService;

    @Autowired
    private PaymentValidationServiceCorrect validationService;

    @Test
    void correctImplementationStartsSuccessfully() {
        /*
         * By extracting PaymentValidationRules, we broke the circular dependency.
         * 
         * If we tried to inject RefundServiceIncorrect and PaymentValidationServiceIncorrect
         * via constructor injection, Spring would fail on startup with:
         * BeanCurrentlyInCreationException: Error creating bean with name 'refundServiceIncorrect':
         * Requested bean is currently in creation: Is there an unresolvable circular reference?
         * 
         * Note: Spring Boot 2.6+ disables circular references by default even for setter/field injection.
         */
        assertNotNull(refundService);
        assertNotNull(validationService);
    }
}
