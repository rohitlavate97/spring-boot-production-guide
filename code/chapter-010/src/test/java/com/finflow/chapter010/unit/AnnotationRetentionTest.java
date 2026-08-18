package com.finflow.chapter010.unit;

import com.finflow.chapter010.correct.Auditable;
import com.finflow.chapter010.incorrect.AuditableIncorrect;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class AnnotationRetentionTest {

    static class TestFixture {

        @AuditableIncorrect(action = "CREATE", resourceType = "ORDER")
        public void incorrectAnnotatedMethod() {
        }

        @Auditable(action = "UPDATE", resourceType = "PAYMENT")
        public void correctAnnotatedMethod() {
        }
    }

    @Test
    void incorrectAnnotation_shouldNotBeVisibleAtRuntime() throws NoSuchMethodException {
        Method method = TestFixture.class.getMethod("incorrectAnnotatedMethod");
        
        AuditableIncorrect annotation = method.getAnnotation(AuditableIncorrect.class);
        
        assertNull(annotation, "Annotation with RetentionPolicy.CLASS should NOT be visible via reflection at runtime");
    }

    @Test
    void correctAnnotation_shouldBeVisibleAtRuntime() throws NoSuchMethodException {
        Method method = TestFixture.class.getMethod("correctAnnotatedMethod");
        
        Auditable annotation = method.getAnnotation(Auditable.class);
        
        assertNotNull(annotation, "Annotation with RetentionPolicy.RUNTIME should be visible via reflection");
        assertEquals("UPDATE", annotation.action());
        assertEquals("PAYMENT", annotation.resourceType());
    }
}
