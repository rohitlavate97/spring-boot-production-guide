package com.finflow.troubleshooting.module21.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class DistributedLockService {

    private static final Logger log = LoggerFactory.getLogger(DistributedLockService.class);

    public record LockRecord(String ownerId, Instant expiresAt) {
        public boolean isExpired() {
            return Instant.now().isAfter(expiresAt);
        }
    }

    private final Map<String, LockRecord> lockTable = new ConcurrentHashMap<>();

    private final AtomicLong locksAcquiredCount = new AtomicLong(0);
    private final AtomicLong locksReleasedSafelyCount = new AtomicLong(0);
    private final AtomicLong foreignLockReleasePreventedCount = new AtomicLong(0);
    private final AtomicLong foreignLockReleasedUnsafelyCount = new AtomicLong(0);

    /**
     * Atomic Lock Acquisition (Equivalent to: SET key ownerId NX PX leaseMs)
     */
    public synchronized boolean tryAcquire(String resourceKey, String ownerId, long leaseMs) {
        LockRecord existing = lockTable.get(resourceKey);
        if (existing == null || existing.isExpired()) {
            LockRecord newLock = new LockRecord(ownerId, Instant.now().plusMillis(leaseMs));
            lockTable.put(resourceKey, newLock);
            locksAcquiredCount.incrementAndGet();
            log.info("[LOCK ACQUIRED] Key={} Owner={} Lease={}ms", resourceKey, ownerId, leaseMs);
            return true;
        }
        return false;
    }

    /**
     * Safe Lock Release using Lua Script Simulation:
     * Only deletes the lock IF the current value matches ownerId.
     */
    public synchronized boolean releaseSafely(String resourceKey, String ownerId) {
        LockRecord current = lockTable.get(resourceKey);
        if (current == null) {
            return false;
        }

        if (current.ownerId().equals(ownerId)) {
            lockTable.remove(resourceKey);
            locksReleasedSafelyCount.incrementAndGet();
            log.info("[LOCK RELEASED SAFELY] Key={} Owner={}", resourceKey, ownerId);
            return true;
        } else {
            // Lock expired and was re-acquired by another process!
            foreignLockReleasePreventedCount.incrementAndGet();
            log.warn("[LUA SCRIPT PREVENTED DISASTER] Owner {} attempted to release lock owned by {}!",
                    ownerId, current.ownerId());
            return false;
        }
    }

    /**
     * ❌ Anti-Pattern: Unsafe Blind DEL
     * Blindly deletes the key without checking ownerId, releasing other processes' locks!
     */
    public synchronized boolean releaseUnsafely(String resourceKey, String ownerId) {
        LockRecord current = lockTable.get(resourceKey);
        if (current != null && !current.ownerId().equals(ownerId)) {
            foreignLockReleasedUnsafelyCount.incrementAndGet();
            log.error("[MUTUAL EXCLUSION BROKEN] Blind DEL released lock owned by {} on behalf of {}!",
                    current.ownerId(), ownerId);
        }
        lockTable.remove(resourceKey);
        return true;
    }

    public synchronized void forceExpireLock(String resourceKey) {
        LockRecord record = lockTable.get(resourceKey);
        if (record != null) {
            lockTable.put(resourceKey, new LockRecord(record.ownerId(), Instant.now().minusSeconds(1)));
        }
    }

    public void clear() {
        lockTable.clear();
        locksAcquiredCount.set(0);
        locksReleasedSafelyCount.set(0);
        foreignLockReleasePreventedCount.set(0);
        foreignLockReleasedUnsafelyCount.set(0);
    }

    public Map<String, Object> getLockStats() {
        return Map.of(
                "activeLocks", lockTable.size(),
                "locksAcquiredCount", locksAcquiredCount.get(),
                "locksReleasedSafelyCount", locksReleasedSafelyCount.get(),
                "foreignLockReleasePreventedCount", foreignLockReleasePreventedCount.get(),
                "foreignLockReleasedUnsafelyCount", foreignLockReleasedUnsafelyCount.get()
        );
    }
}
