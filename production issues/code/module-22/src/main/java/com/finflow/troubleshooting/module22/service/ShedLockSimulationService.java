package com.finflow.troubleshooting.module22.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ShedLockSimulationService {

    private static final Logger log = LoggerFactory.getLogger(ShedLockSimulationService.class);

    public record LockState(String lockedBy, Instant lockUntil, Instant lockedAt) {
        public boolean isLocked() {
            return Instant.now().isBefore(lockUntil);
        }
    }

    private final Map<String, LockState> lockTable = new ConcurrentHashMap<>();

    private final AtomicLong totalLockAttempts = new AtomicLong(0);
    private final AtomicLong successfulExecutions = new AtomicLong(0);
    private final AtomicLong preventedDuplicateExecutions = new AtomicLong(0);

    /**
     * Simulates ShedLock distributed locking:
     * - lockAtMostFor: Auto-release if node crashes mid-execution.
     * - lockAtLeastFor: Prevents clock skew / rapid re-execution on another node.
     */
    public synchronized boolean executeWithLock(String lockName, String nodeId,
                                                long lockAtMostForMs, long lockAtLeastForMs,
                                                Runnable task) {
        totalLockAttempts.incrementAndGet();
        LockState current = lockTable.get(lockName);

        if (current != null && current.isLocked()) {
            preventedDuplicateExecutions.incrementAndGet();
            log.info("[SHEDLOCK PREVENTED DUPLICATION] Node {} skipped scheduled job '{}' (currently locked by {} until {})",
                    nodeId, lockName, current.lockedBy(), current.lockUntil());
            return false;
        }

        Instant lockedAt = Instant.now();
        Instant lockUntil = lockedAt.plusMillis(lockAtMostForMs);
        lockTable.put(lockName, new LockState(nodeId, lockUntil, lockedAt));
        successfulExecutions.incrementAndGet();

        log.info("[SHEDLOCK LOCK ACQUIRED] Node {} executing scheduled job '{}'", nodeId, lockName);

        try {
            task.run();
        } finally {
            // After task finishes, retain lock until lockAtLeastFor expires (guards against clock skew)
            Instant finishedAt = Instant.now();
            Instant leastUntil = lockedAt.plusMillis(lockAtLeastForMs);
            Instant effectiveUntil = finishedAt.isAfter(leastUntil) ? finishedAt : leastUntil;
            lockTable.put(lockName, new LockState(nodeId, effectiveUntil, lockedAt));
            log.info("[SHEDLOCK JOB FINISHED] Node {} completed '{}'. Lock held until {}",
                    nodeId, lockName, effectiveUntil);
        }
        return true;
    }

    public synchronized void forceExpireLock(String lockName) {
        lockTable.remove(lockName);
    }

    public void clear() {
        lockTable.clear();
        totalLockAttempts.set(0);
        successfulExecutions.set(0);
        preventedDuplicateExecutions.set(0);
    }

    public Map<String, Object> getStats() {
        return Map.of(
                "activeLocks", lockTable.size(),
                "totalLockAttempts", totalLockAttempts.get(),
                "successfulExecutions", successfulExecutions.get(),
                "preventedDuplicateExecutions", preventedDuplicateExecutions.get()
        );
    }
}
