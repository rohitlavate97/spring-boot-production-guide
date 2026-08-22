package com.finflow.troubleshooting.module21;

import com.finflow.troubleshooting.module21.service.DistributedLockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DistributedLockSafetyTest {

    private DistributedLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new DistributedLockService();
        lockService.clear();
    }

    @Test
    @DisplayName("Should safely acquire and release lock when owned by the same process")
    void testSafeAcquireAndRelease() {
        String resource = "lock:wallet:ACC-555";
        String ownerId = "PROCESS_A";

        boolean acquired = lockService.tryAcquire(resource, ownerId, 2000);
        assertThat(acquired).isTrue();

        boolean released = lockService.releaseSafely(resource, ownerId);
        assertThat(released).isTrue();
    }

    @Test
    @DisplayName("Safe Lua release MUST reject releasing a lock if the lease expired and another process acquired it")
    void testSafeLuaReleasePreventsForeignLockRelease() {
        String resource = "lock:wallet:ACC-777";
        String processA = "PROCESS_A";
        String processB = "PROCESS_B";

        // 1. Process A acquires lock
        assertThat(lockService.tryAcquire(resource, processA, 500)).isTrue();

        // 2. Process A lease expires
        lockService.forceExpireLock(resource);

        // 3. Process B acquires the expired lock
        assertThat(lockService.tryAcquire(resource, processB, 5000)).isTrue();

        // 4. Process A finishes and attempts to release the lock!
        boolean processAReleaseResult = lockService.releaseSafely(resource, processA);

        // Crucial validation: Process A's release MUST FAIL!
        assertThat(processAReleaseResult).isFalse();

        // Verify Process B still owns the lock
        assertThat(lockService.getLockStats().get("foreignLockReleasePreventedCount")).isEqualTo(1L);

        // Process B can release its own lock cleanly
        assertThat(lockService.releaseSafely(resource, processB)).isTrue();
    }
}
