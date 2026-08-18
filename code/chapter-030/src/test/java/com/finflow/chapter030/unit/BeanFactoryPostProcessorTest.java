package com.finflow.chapter030.unit;

import com.finflow.chapter030.incorrect.AuditableBeanRegistrarIncorrect;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class BeanFactoryPostProcessorTest {

    @Component
    static class DependencyBean {}

    @Component
    static class TargetBean {
        @Autowired
        private DependencyBean dependency;

        public DependencyBean getDependency() {
            return dependency;
        }
    }

    @Configuration
    static class TestConfig {}

    @Test
    void testPrematureInstantiationCausesNullAutowired() {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        
        // Register the incorrect registrar and our test beans
        context.register(AuditableBeanRegistrarIncorrect.class);
        context.register(DependencyBean.class);
        context.register(TargetBean.class);
        
        context.refresh();

        TargetBean bean = context.getBean(TargetBean.class);
        
        // Because AuditableBeanRegistrarIncorrect called getBean() during the BeanFactoryPostProcessor phase,
        // the TargetBean was fully initialized before AutowiredAnnotationBeanPostProcessor was registered.
        // Therefore, @Autowired processing was skipped!
        assertNull(bean.getDependency(), "Dependency should be null due to premature instantiation by BeanFactoryPostProcessor");
        
        context.close();
    }
}
