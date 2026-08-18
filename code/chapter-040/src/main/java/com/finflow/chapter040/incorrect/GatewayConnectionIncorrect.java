package com.finflow.chapter040.incorrect;

import com.finflow.chapter040.domain.PaymentRequest;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;

@Component
@Scope("prototype")
public class GatewayConnectionIncorrect {
    private static final Logger log = LoggerFactory.getLogger(GatewayConnectionIncorrect.class);
    
    public static final AtomicInteger instanceCount = new AtomicInteger(0);
    public static final AtomicInteger preDestroyCount = new AtomicInteger(0);
    
    private final int connectionId;
    private boolean open;

    public GatewayConnectionIncorrect() {
        this.connectionId = instanceCount.incrementAndGet();
    }

    @PostConstruct
    public void openConnection() {
        this.open = true;
        log.info("Opened simulated gateway connection #{}", connectionId);
    }

    @PreDestroy
    public void closeConnection() {
        this.open = false;
        preDestroyCount.incrementAndGet();
        log.info("Closed simulated gateway connection #{} (This will NOT log for prototypes)", connectionId);
    }

    public void send(PaymentRequest request) {
        if (!open) {
            throw new IllegalStateException("Connection is closed!");
        }
        log.info("Sending payment {} via connection #{}", request.paymentIntentId(), connectionId);
    }
    
    public int getConnectionId() {
        return connectionId;
    }
}
