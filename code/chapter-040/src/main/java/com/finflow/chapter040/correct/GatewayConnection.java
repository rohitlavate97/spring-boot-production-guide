package com.finflow.chapter040.correct;

import com.finflow.chapter040.domain.PaymentRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicInteger;

// NOT a Spring bean! Plain POJO created by the pool
public class GatewayConnection implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(GatewayConnection.class);
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(0);

    private final int id;
    private boolean open;

    public GatewayConnection() {
        this.id = ID_GENERATOR.incrementAndGet();
        this.open = true;
        log.info("Opened raw gateway connection #{}", id);
    }

    public void send(PaymentRequest request) {
        if (!open) {
            throw new IllegalStateException("Connection is closed!");
        }
        log.info("Sending payment {} via pooled connection #{}", request.paymentIntentId(), id);
    }

    @Override
    public void close() {
        this.open = false;
        log.info("Closed raw gateway connection #{}", id);
    }
    
    public int getId() {
        return id;
    }
}
