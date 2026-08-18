package com.finflow.chapter010.incorrect;

import com.finflow.chapter010.domain.DomainEvent;
import com.finflow.chapter010.domain.DomainEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;

@Component
public class NaiveEventRouter {

    private static final Logger log = LoggerFactory.getLogger(NaiveEventRouter.class);

    private final List<DomainEventHandler<?>> handlers;

    public NaiveEventRouter(List<DomainEventHandler<?>> handlers) {
        this.handlers = handlers;
    }

    @SuppressWarnings("unchecked")
    public void route(DomainEvent event) {
        boolean handled = false;
        
        for (DomainEventHandler<?> handler : handlers) {
            try {
                // Bug: this gets the TypeVariable, not the actual generic class argument if it's behind proxies or multiple interfaces
                Type[] interfaces = handler.getClass().getGenericInterfaces();
                for (Type type : interfaces) {
                    if (type instanceof ParameterizedType parameterizedType) {
                        Type typeArg = parameterizedType.getActualTypeArguments()[0];
                        // This usually fails to equal the concrete event class, it might be a TypeVariable or wildcard
                        if (typeArg.getTypeName().equals(event.getClass().getName())) {
                            log.info("Routing event {} to handler {}", event.getClass().getSimpleName(), handler.getClass().getSimpleName());
                            ((DomainEventHandler<DomainEvent>) handler).handle(event);
                            handled = true;
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("Failed to resolve type for handler {}", handler.getClass().getSimpleName(), e);
            }
        }
        
        if (!handled) {
            log.warn("Falling back to broadcasting event {} to all handlers", event.getClass().getSimpleName());
            for (DomainEventHandler<?> handler : handlers) {
                ((DomainEventHandler<DomainEvent>) handler).handle(event);
            }
        }
    }
}
