package com.finflow.chapter030.unit;

import com.finflow.chapter030.correct.FraudDetectionService;
import com.finflow.chapter030.correct.PaymentProcessingService;
import com.finflow.chapter030.correct.PaymentValidationService;
import com.finflow.chapter030.incorrect.FraudDetectionServiceIncorrect;
import com.finflow.chapter030.incorrect.PaymentProcessingServiceIncorrect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanCurrentlyInCreationException;
import org.springframework.beans.factory.UnsatisfiedDependencyException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class CircularDependencyTest {

    @Test
    void testCircularDependencyThrowsException() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        
        context.register(FraudDetectionServiceIncorrect.class);
        context.register(PaymentProcessingServiceIncorrect.class);
        
        // This will throw because of circular constructor injection
        Exception exception = assertThrows(UnsatisfiedDependencyException.class, context::refresh);
        
        assertTrue(exception.getCause() instanceof BeanCurrentlyInCreationException || 
                   exception.getMessage().contains("Requested bean is currently in creation"));
        
        context.close();
    }

    @Test
    void testExtractedServiceResolvesCircularDependency() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        
        // Using mocked/stubbed repository to satisfy PaymentValidationService
        context.registerBean(com.finflow.chapter030.repository.PaymentIntentRepository.class, 
            () -> org.mockito.Mockito.mock(com.finflow.chapter030.repository.PaymentIntentRepository.class));
            
        context.register(PaymentValidationService.class);
        context.register(FraudDetectionService.class);
        context.register(PaymentProcessingService.class);
        
        // Should start successfully
        context.refresh();
        
        assertNotNull(context.getBean(PaymentProcessingService.class));
        assertNotNull(context.getBean(FraudDetectionService.class));
        
        context.close();
    }
}
