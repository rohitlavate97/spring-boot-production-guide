package com.finflow.chapter280.correct;

import net.javacrumbs.shedlock.core.DefaultLockingTaskExecutor;
import net.javacrumbs.shedlock.core.LockConfiguration;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.LockingTaskExecutor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class MultiPodShedLockSimulator {

    private final LockingTaskExecutor lockingTaskExecutor;
    private final AtomicInteger successCount = new AtomicInteger(0);
    private final AtomicInteger blockedCount = new AtomicInteger(0);

    public MultiPodShedLockSimulator(LockProvider lockProvider) {
        this.lockingTaskExecutor = new DefaultLockingTaskExecutor(lockProvider);
    }

    /**
     * Simulates a pod attempting to execute a scheduled task protected by ShedLock.
     * Returns true if lock was acquired and task was executed, false if blocked by another pod.
     */
    public boolean executeAsPod(String podName, String taskName, Duration lockAtMostFor, Duration lockAtLeastFor) {
        LockConfiguration lockConfig = new LockConfiguration(
                Instant.now(),
                taskName,
                lockAtMostFor,
                lockAtLeastFor
        );

        AtomicBoolean executed = new AtomicBoolean(false);
        try {
            lockingTaskExecutor.executeWithLock((LockingTaskExecutor.Task) () -> {
                executed.set(true);
                successCount.incrementAndGet();
            }, lockConfig);
        } catch (Throwable ignored) {
        }

        if (!executed.get()) {
            blockedCount.incrementAndGet();
        }

        return executed.get();
    }

    public int getSuccessCount() { return successCount.get(); }
    public int getBlockedCount() { return blockedCount.get(); }
    public void reset() {
        successCount.set(0);
        blockedCount.set(0);
    }
}
