package com.finflow.chapter010.correct;

import com.finflow.chapter010.domain.DomainEventHandler;
import com.finflow.chapter010.domain.PaymentCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PaymentCompletedHandler implements DomainEventHandler<PaymentCompletedEvent> {
    
    private static final Logger log = LoggerFactory.getLogger(PaymentCompletedHandler.class);
    
    @Override
    public void handle(PaymentCompletedEvent event) {
        log.info("Processing payment completed: intentId={}, amount={}{}",
            event.getPaymentIntentId(), event.getAmountCents(), event.getCurrency());
    }
    
    @Override
    public Class<PaymentCompletedEvent> getEventType() {
        return PaymentCompletedEvent.class;
    }
}
