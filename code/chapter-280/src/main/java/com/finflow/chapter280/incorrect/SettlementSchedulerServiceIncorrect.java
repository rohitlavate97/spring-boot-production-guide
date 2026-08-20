package com.finflow.chapter280.incorrect;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * INCORRECT IMPLEMENTATION:
 * 1. No distributed locking -> All running pods execute the midnight cron job simultaneously!
 * 2. Unconfigured TaskScheduler uses poolSize=1 -> Long tasks starve short tasks.
 */
@Service
public class SettlementSchedulerServiceIncorrect {

    private final AtomicInteger duplicateExecutionCount = new AtomicInteger(0);

    /**
     * Anti-Pattern: @Scheduled without @SchedulerLock across multiple pods.
     * When 8 pods run this method at midnight, all 8 execute the settlement job!
     */
    public void executeUnlockedCronAcrossPods(String podName) {
        duplicateExecutionCount.incrementAndGet();
    }

    public int getDuplicateExecutionCount() {
        return duplicateExecutionCount.get();
    }

    public void reset() {
        duplicateExecutionCount.set(0);
    }
}
