package com.finflow.chapter010.correct;

import com.finflow.chapter010.domain.DomainEvent;
import com.finflow.chapter010.domain.DomainEventHandler;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class TypeAwareEventRouter {

    private static final Logger log = LoggerFactory.getLogger(TypeAwareEventRouter.class);

    private final List<DomainEventHandler<?>> handlers;
    private final Map<Class<? extends DomainEvent>, List<DomainEventHandler<?>>> registry = new ConcurrentHashMap<>();

    public TypeAwareEventRouter(List<DomainEventHandler<?>> handlers) {
        this.handlers = handlers;
    }

    @PostConstruct
    public void buildRegistry() {
        for (DomainEventHandler<?> handler : handlers) {
            // Using Spring's ResolvableType to correctly extract generic parameter
            ResolvableType resolvableType = ResolvableType.forClass(handler.getClass())
                    .as(DomainEventHandler.class);
            
            Class<?> eventType = resolvableType.getGeneric(0).resolve();
            
            if (eventType != null && DomainEvent.class.isAssignableFrom(eventType)) {
                @SuppressWarnings("unchecked")
                Class<? extends DomainEvent> eventClass = (Class<? extends DomainEvent>) eventType;
                
                registry.computeIfAbsent(eventClass, k -> new ArrayList<>()).add(handler);
                log.info("Registered handler {} for event type {}", handler.getClass().getSimpleName(), eventClass.getSimpleName());
            } else {
                log.warn("Could not resolve event type for handler {}", handler.getClass().getSimpleName());
            }
        }
    }

    @SuppressWarnings("unchecked")
    public void route(DomainEvent event) {
        Class<? extends DomainEvent> eventType = event.getClass();
        
        List<DomainEventHandler<?>> eventHandlers = registry.getOrDefault(eventType, List.of());
        
        if (eventHandlers.isEmpty()) {
            log.warn("No handlers found for event type: {}", eventType.getSimpleName());
            return;
        }

        for (DomainEventHandler<?> handler : eventHandlers) {
            log.debug("Routing event {} to handler {}", eventType.getSimpleName(), handler.getClass().getSimpleName());
            ((DomainEventHandler<DomainEvent>) handler).handle(event);
        }
    }
}
