package com.finflow.chapter040.correct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class GracefulShutdownService implements SmartLifecycle {
    private static final Logger log = LoggerFactory.getLogger(GracefulShutdownService.class);
    
    private final AtomicBoolean isRunning = new AtomicBoolean(false);
    private final List<String> inFlightPayments = new CopyOnWriteArrayList<>();
    
    private static final long MAX_SHUTDOWN_MILLIS = 20_000; // Leave 10s buffer before K8s 30s SIGKILL

    public void addInFlight(String paymentId) {
        inFlightPayments.add(paymentId);
    }
    
    @Override
    public void start() {
        isRunning.set(true);
    }

    @Override
    public void stop() {
        stop(() -> {});
    }

    @Override
    public void stop(Runnable callback) {
        log.info("Starting graceful shutdown. Flushing {} in-flight payments...", inFlightPayments.size());
        long startTime = System.currentTimeMillis();
        
        List<String> batch = new ArrayList<>();
        for (String paymentId : inFlightPayments) {
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed > MAX_SHUTDOWN_MILLIS) {
                log.warn("Deadline approaching! Fallback to fast local file write for remaining {} payments.", inFlightPayments.size());
                writeRemainingToLocalFile(inFlightPayments);
                break;
            }
            
            batch.add(paymentId);
            if (batch.size() >= 50) {
                flushBatch(batch);
                batch.clear();
            }
        }
        
        if (!batch.isEmpty()) {
            flushBatch(batch);
        }
        
        inFlightPayments.clear();
        isRunning.set(false);
        log.info("Graceful shutdown completed successfully.");
        callback.run();
    }

    private void flushBatch(List<String> batch) {
        try {
            // Simulate bulk redis write which is much faster than individually
            Thread.sleep(50);
            inFlightPayments.removeAll(batch);
            log.debug("Flushed batch of {} payments", batch.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
    
    private void writeRemainingToLocalFile(List<String> remaining) {
        // Fast dump to disk logic here
        log.info("Emergency dumped {} payments to disk", remaining.size());
        inFlightPayments.clear();
    }

    @Override
    public boolean isRunning() {
        return isRunning.get();
    }

    @Override
    public int getPhase() {
        // High max value minus one ensures we shutdown early in the lifecycle process
        return Integer.MAX_VALUE - 1;
    }
}
