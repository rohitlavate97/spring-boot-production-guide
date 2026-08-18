package com.finflow.chapter030.unit;

import com.finflow.chapter030.correct.BeanStartupProfiler;
import jakarta.annotation.PostConstruct;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BeanStartupProfilerTest {

    @Component
    static class SlowBean {
        @PostConstruct
        public void init() throws InterruptedException {
            Thread.sleep(1100);
        }
    }

    @Component
    static class FastBean {
        @PostConstruct
        public void init() {
            // no delay
        }
    }

    @Test
    void testProfilerRecordsSlowBeans() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.register(BeanStartupProfiler.class);
        context.register(SlowBean.class);
        context.register(FastBean.class);
        
        context.refresh();

        BeanStartupProfiler profiler = context.getBean(BeanStartupProfiler.class);
        Map<String, Duration> slowBeans = profiler.getSlowBeans(Duration.ofMillis(1000));
        
        assertTrue(slowBeans.containsKey("beanStartupProfilerTest.SlowBean"), "Slow bean should be tracked");
        assertFalse(slowBeans.containsKey("beanStartupProfilerTest.FastBean"), "Fast bean should not be tracked as slow");
        
        context.close();
    }
}
