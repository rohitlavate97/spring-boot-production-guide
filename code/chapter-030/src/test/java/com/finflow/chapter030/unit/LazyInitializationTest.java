package com.finflow.chapter030.unit;

import com.finflow.chapter030.correct.ReportGenerationServiceCorrect;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertTrue;

class LazyInitializationTest {

    @Test
    void testLazyInitializationDefersHeavyWork() {
        long startContext = System.currentTimeMillis();
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(ReportGenerationServiceCorrect.class);
        context.refresh();
        long contextStartupTime = System.currentTimeMillis() - startContext;
        
        // Startup should be very fast because service is @Lazy and internal data is lazy initialized
        assertTrue(contextStartupTime < 1000, "Context startup should be fast");

        ReportGenerationServiceCorrect service = context.getBean(ReportGenerationServiceCorrect.class);
        
        long startGenerate = System.currentTimeMillis();
        String report = service.generateReport();
        long generateTime = System.currentTimeMillis() - startGenerate;
        
        // Generate will take time on first call due to heavy initialization simulation
        assertTrue(generateTime >= 2000, "First call should block for initialization");
        
        long startGenerate2 = System.currentTimeMillis();
        service.generateReport();
        long generateTime2 = System.currentTimeMillis() - startGenerate2;
        
        // Second generate should be instantly fast
        assertTrue(generateTime2 < 100, "Subsequent calls should be fast");
        
        context.close();
    }
}
