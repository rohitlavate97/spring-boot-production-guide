package com.finflow.chapter010.domain;

public interface DomainEventHandler<T extends DomainEvent> {
    void handle(T event);
    Class<T> getEventType();
}
