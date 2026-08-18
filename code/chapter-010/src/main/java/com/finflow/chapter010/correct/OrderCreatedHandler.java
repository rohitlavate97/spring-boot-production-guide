package com.finflow.chapter010.correct;

import com.finflow.chapter010.domain.DomainEventHandler;
import com.finflow.chapter010.domain.OrderCreatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedHandler implements DomainEventHandler<OrderCreatedEvent> {
    
    private static final Logger log = LoggerFactory.getLogger(OrderCreatedHandler.class);
    
    @Override
    public void handle(OrderCreatedEvent event) {
        log.info("Processing order created: orderId={}, customerId={}, totalAmount={}",
            event.getOrderId(), event.getCustomerId(), event.getTotalCents());
    }
    
    @Override
    public Class<OrderCreatedEvent> getEventType() {
        return OrderCreatedEvent.class;
    }
}
