package com.finflow.chapter040.incorrect;

import com.finflow.chapter040.domain.PaymentRequest;
import com.finflow.chapter040.domain.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentProcessorIncorrect {
    private static final Logger log = LoggerFactory.getLogger(PaymentProcessorIncorrect.class);

    // BUG: Injecting a prototype into a singleton means it behaves like a singleton
    @Autowired
    private GatewayConnectionIncorrect connection;

    public PaymentResult process(PaymentRequest request) {
        log.info("Processing payment on injected prototype connection #{}", connection.getConnectionId());
        connection.send(request);
        return new PaymentResult(UUID.randomUUID(), "SUCCESS", request.amountCents(), Instant.now());
    }
    
    public GatewayConnectionIncorrect getConnection() {
        return connection;
    }
}
