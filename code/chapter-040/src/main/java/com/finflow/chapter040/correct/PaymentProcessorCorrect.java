package com.finflow.chapter040.correct;

import com.finflow.chapter040.domain.PaymentRequest;
import com.finflow.chapter040.domain.PaymentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentProcessorCorrect {
    private static final Logger log = LoggerFactory.getLogger(PaymentProcessorCorrect.class);

    private final GatewayConnectionPool connectionPool;

    @Autowired
    public PaymentProcessorCorrect(GatewayConnectionPool connectionPool) {
        this.connectionPool = connectionPool;
    }

    public PaymentResult process(PaymentRequest request) {
        GatewayConnection connection = null;
        try {
            connection = connectionPool.acquire();
            log.info("Processing payment via connection #{}", connection.getId());
            connection.send(request);
            return new PaymentResult(UUID.randomUUID(), "SUCCESS", request.amountCents(), Instant.now());
        } finally {
            connectionPool.release(connection);
        }
    }
}
