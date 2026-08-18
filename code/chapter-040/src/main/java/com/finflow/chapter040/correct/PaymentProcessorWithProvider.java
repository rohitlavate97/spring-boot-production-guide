package com.finflow.chapter040.correct;

import com.finflow.chapter040.domain.PaymentRequest;
import com.finflow.chapter040.domain.PaymentResult;
import com.finflow.chapter040.incorrect.GatewayConnectionIncorrect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class PaymentProcessorWithProvider implements DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(PaymentProcessorWithProvider.class);

    // Using ObjectProvider to fetch a fresh prototype instance on every call
    private final ObjectProvider<GatewayConnectionIncorrect> connectionProvider;
    
    // We must track prototypes to close them ourselves if they hold resources
    private final List<GatewayConnectionIncorrect> createdConnections = new CopyOnWriteArrayList<>();

    @Autowired
    public PaymentProcessorWithProvider(ObjectProvider<GatewayConnectionIncorrect> connectionProvider) {
        this.connectionProvider = connectionProvider;
    }

    public PaymentResult process(PaymentRequest request) {
        GatewayConnectionIncorrect connection = connectionProvider.getObject();
        createdConnections.add(connection);
        
        log.info("Processing payment via fresh prototype connection #{}", connection.getConnectionId());
        connection.send(request);
        return new PaymentResult(UUID.randomUUID(), "SUCCESS", request.amountCents(), Instant.now());
    }

    @Override
    public void destroy() throws Exception {
        log.info("Cleaning up {} prototype connections manually", createdConnections.size());
        for (GatewayConnectionIncorrect conn : createdConnections) {
            conn.closeConnection(); // Manually triggering cleanup
        }
        createdConnections.clear();
    }
}
