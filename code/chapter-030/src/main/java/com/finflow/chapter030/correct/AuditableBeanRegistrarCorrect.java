package com.finflow.chapter030.correct;

import com.finflow.chapter030.incorrect.Auditable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

@Component
public class AuditableBeanRegistrarCorrect implements BeanPostProcessor {
    private static final Logger log = LoggerFactory.getLogger(AuditableBeanRegistrarCorrect.class);

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // CORRECT APPROACH: We are in the BeanPostProcessor phase.
        // The bean is already instantiated and autowired. We just inspect it.
        for (Method method : bean.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(Auditable.class)) {
                log.info("Correctly registered @Auditable method: {} in bean: {}", method.getName(), beanName);
            }
        }
        return bean; // Return the bean instance (could also be a proxy if we wanted)
    }
}
