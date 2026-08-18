package com.finflow.chapter020.unit;

import com.finflow.chapter020.correct.WarmupReadinessIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WarmupReadinessIndicatorTest {

    @Test
    void testWarmupStateTransitions() {
        WarmupReadinessIndicator indicator = new WarmupReadinessIndicator();
        
        // Initially should be DOWN
        Health initialHealth = indicator.health();
        assertEquals(Status.DOWN, initialHealth.getStatus());
        assertEquals("in_progress", initialHealth.getDetails().get("warmup"));
        
        // After warmup complete, should be UP
        indicator.setWarmupComplete(true);
        Health finalHealth = indicator.health();
        assertEquals(Status.UP, finalHealth.getStatus());
        assertEquals("completed", finalHealth.getDetails().get("warmup"));
    }
}
