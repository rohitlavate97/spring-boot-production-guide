package com.finflow.chapter020.correct;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;

@Component("warmup")
public class WarmupReadinessIndicator implements HealthIndicator {

    // Thread-safe flag to track warmup state
    private final AtomicBoolean warmupComplete = new AtomicBoolean(false);

    public void setWarmupComplete(boolean status) {
        this.warmupComplete.set(status);
    }

    @Override
    public Health health() {
        if (warmupComplete.get()) {
            return Health.up().withDetail("warmup", "completed").build();
        } else {
            // Application is UP but not READY for traffic yet due to cold JIT
            return Health.down().withDetail("warmup", "in_progress").build();
        }
    }
}
