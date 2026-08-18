package com.finflow.chapter030.incorrect;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import java.lang.reflect.Method;

/**
 * INCORRECT IMPLEMENTATION: Do not use @Component here if scanning in the same context as correct implementations.
 * We omit @Component so it doesn't break the main context, but we will register it manually in tests.
 */
public class AuditableBeanRegistrarIncorrect implements BeanFactoryPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(AuditableBeanRegistrarIncorrect.class);

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        log.warn("Executing AuditableBeanRegistrarIncorrect - THIS IS AN ANTI-PATTERN");
        String[] beanNames = beanFactory.getBeanDefinitionNames();
        
        for (String beanName : beanNames) {
            try {
                // BUG: Calling getBean() inside a BeanFactoryPostProcessor forces premature initialization.
                // At this phase, BeanPostProcessors (like AutowiredAnnotationBeanPostProcessor) are NOT yet registered!
                // Any bean instantiated here will have @Autowired fields remain null.
                Object bean = beanFactory.getBean(beanName);
                
                for (Method method : bean.getClass().getDeclaredMethods()) {
                    if (method.isAnnotationPresent(Auditable.class)) {
                        log.info("Found @Auditable method: {} in bean: {}", method.getName(), beanName);
                    }
                }
            } catch (Exception e) {
                log.trace("Could not inspect bean {} for @Auditable", beanName);
            }
        }
    }
}
