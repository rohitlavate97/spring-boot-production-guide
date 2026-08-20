package com.finflow.chapter290.incorrect;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. Default SimpleAsyncTaskExecutor creates a new platform thread per task -> OutOfMemoryError.
 * 2. Exceptions in @Async void methods are swallowed silently.
 */
@Service
public class StatementExportServiceIncorrect {

    private final AtomicInteger spawnedThreadCount = new AtomicInteger(0);

    /**
     * Anti-Pattern: Unpooled thread creation.
     */
    public void executeUnboundedAsync() {
        // Simulates unpooled thread creation
        spawnedThreadCount.incrementAndGet();
    }

    public int getSpawnedThreadCount() {
        return spawnedThreadCount.get();
    }

    public void reset() {
        spawnedThreadCount.set(0);
    }
}
