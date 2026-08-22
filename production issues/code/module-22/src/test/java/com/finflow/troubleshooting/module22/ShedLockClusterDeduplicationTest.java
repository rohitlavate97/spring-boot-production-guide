package com.finflow.troubleshooting.module22;

import com.finflow.troubleshooting.module22.job.ResilientBillingJob;
import com.finflow.troubleshooting.module22.service.ShedLockSimulationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

class ShedLockClusterDeduplicationTest {

    private ShedLockSimulationService shedLockService;
    private ResilientBillingJob billingJob;

    @BeforeEach
    void setUp() {
        shedLockService = new ShedLockSimulationService();
        billingJob = new ResilientBillingJob(shedLockService);
        billingJob.reset();
        shedLockService.clear();
    }

    @Test
    @DisplayName("When 6 cluster pod replicas fire scheduled billing simultaneously, ShedLock MUST ensure EXACTLY 1 executes")
    void testShedLockPreventsClusterDuplication() throws Exception {
        int clusterPods = 6;
        ExecutorService executor = Executors.newFixedThreadPool(clusterPods);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 1; i <= clusterPods; i++) {
            final String nodeId = "finflow-pod-replica-" + i;
            tasks.add(() -> billingJob.executeClusterSafeBilling(nodeId));
        }

        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        int successCount = 0;
        for (Future<Boolean> f : futures) {
            if (f.get()) successCount++;
        }
        executor.shutdown();

        // Exact invariant: EXACTLY 1 pod acquired the lock and executed billing!
        assertThat(successCount).isEqualTo(1);
        assertThat(billingJob.getProtectedBillingExecutionCount()).isEqualTo(1);
        assertThat(shedLockService.getStats().get("preventedDuplicateExecutions")).isEqualTo(5L);
    }
}
