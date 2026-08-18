package com.finflow.chapter030.correct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class BeanStartupProfiler implements BeanPostProcessor, Ordered {
    private static final Logger log = LoggerFactory.getLogger(BeanStartupProfiler.class);

    private final Map<String, Long> startTimes = new ConcurrentHashMap<>();
    private final Map<String, Duration> initializationDurations = new ConcurrentHashMap<>();

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) throws BeansException {
        startTimes.put(beanName, System.currentTimeMillis());
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        Long startTime = startTimes.remove(beanName);
        if (startTime != null) {
            long durationMs = System.currentTimeMillis() - startTime;
            Duration duration = Duration.ofMillis(durationMs);
            initializationDurations.put(beanName, duration);
            
            if (durationMs > 1000) {
                log.warn("SLOW BEAN INITIALIZATION: Bean '{}' took {} ms", beanName, durationMs);
            }
        }
        return bean;
    }

    public Map<String, Duration> getSlowBeans(Duration threshold) {
        return initializationDurations.entrySet().stream()
                .filter(entry -> entry.getValue().compareTo(threshold) >= 0)
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
