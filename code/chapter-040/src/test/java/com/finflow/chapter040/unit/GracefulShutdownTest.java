package com.finflow.chapter040.unit;

import com.finflow.chapter040.correct.GracefulShutdownService;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GracefulShutdownTest {

    @Test
    void gracefulShutdownCompletesWithinTimeout() {
        GracefulShutdownService service = new GracefulShutdownService();
        service.start();
        
        for (int i = 0; i < 200; i++) {
            service.addInFlight("payment-" + i);
        }
        
        assertTrue(service.isRunning());
        
        AtomicBoolean callbackTriggered = new AtomicBoolean(false);
        service.stop(() -> callbackTriggered.set(true));
        
        // Assert it properly stopped
        assertFalse(service.isRunning());
        assertTrue(callbackTriggered.get());
    }
}
