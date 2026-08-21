package com.finflow.troubleshooting.module13.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.concurrent.locks.ReentrantLock;

@Service
public class VirtualThreadPinningService {

    private static final Logger log = LoggerFactory.getLogger(VirtualThreadPinningService.class);

    private final Object monitorLock = new Object();
    private final ReentrantLock reentrantLock = new ReentrantLock();

    // ❌ ANTI-PATTERN in Virtual Threads: synchronized block pins the carrier thread during blocking I/O
    public String executeSynchronizedTask(long durationMs) {
        synchronized (monitorLock) {
            log.info("[PinningDemo] Executing synchronized task on thread: {}", Thread.currentThread());
            try {
                Thread.sleep(durationMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "SYNCHRONIZED_COMPLETED";
        }
    }

    // ✅ BEST PRACTICE in Virtual Threads: ReentrantLock allows the virtual thread to unmount from carrier
    public String executeReentrantLockTask(long durationMs) {
        reentrantLock.lock();
        try {
            log.info("[UnmountedDemo] Executing ReentrantLock task on thread: {}", Thread.currentThread());
            try {
                Thread.sleep(durationMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return "REENTRANT_LOCK_COMPLETED";
        } finally {
            reentrantLock.unlock();
        }
    }
}
