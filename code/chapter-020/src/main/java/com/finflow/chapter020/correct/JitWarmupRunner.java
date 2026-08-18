package com.finflow.chapter020.correct;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class JitWarmupRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(JitWarmupRunner.class);
    
    private final WarmupReadinessIndicator readinessIndicator;

    public JitWarmupRunner(WarmupReadinessIndicator readinessIndicator) {
        this.readinessIndicator = readinessIndicator;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("Starting JIT Warmup sequence...");
        long start = System.currentTimeMillis();
        
        try {
            // Simulate firing synthetic requests to warm up hot paths
            // In a real scenario, this would use a WebClient or RestTemplate 
            // to hit local endpoints like /api/payments several thousand times
            // to trigger C1 and C2 (tiered) compilation.
            for (int i = 0; i < 5000; i++) {
                simulateWarmupWork();
            }
            
            log.info("JIT Warmup sequence completed in {} ms.", System.currentTimeMillis() - start);
        } catch (Exception e) {
            log.warn("JIT Warmup encountered an error, continuing anyway", e);
        } finally {
            // Mark application as fully ready to receive traffic
            readinessIndicator.setWarmupComplete(true);
        }
    }
    
    private void simulateWarmupWork() {
        // Just dummy work for demonstration purposes
        Math.pow(Math.random(), Math.random());
    }
}
