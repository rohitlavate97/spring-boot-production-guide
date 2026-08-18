package com.finflow.chapter040.correct;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

@Component
public class GatewayConnectionPool {
    private static final Logger log = LoggerFactory.getLogger(GatewayConnectionPool.class);
    private static final int POOL_SIZE = 5;

    private final BlockingQueue<GatewayConnection> pool = new ArrayBlockingQueue<>(POOL_SIZE);

    @PostConstruct
    public void initPool() {
        log.info("Initializing Gateway Connection Pool of size {}", POOL_SIZE);
        for (int i = 0; i < POOL_SIZE; i++) {
            pool.offer(new GatewayConnection());
        }
    }

    public GatewayConnection acquire() {
        try {
            GatewayConnection conn = pool.poll(5, TimeUnit.SECONDS);
            if (conn == null) {
                throw new IllegalStateException("Timeout acquiring connection from pool");
            }
            return conn;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted while acquiring connection", e);
        }
    }

    public void release(GatewayConnection connection) {
        if (connection != null) {
            pool.offer(connection);
        }
    }

    @PreDestroy
    public void shutdownPool() {
        log.info("Shutting down Gateway Connection Pool...");
        GatewayConnection conn;
        while ((conn = pool.poll()) != null) {
            conn.close();
        }
    }
}
