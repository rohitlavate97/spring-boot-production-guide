package com.finflow.troubleshooting.module22.controller;

import com.finflow.troubleshooting.module22.job.ResilientBillingJob;
import com.finflow.troubleshooting.module22.service.ShedLockSimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

@RestController
@RequestMapping("/api/v1/scheduler")
public class SchedulerDiagnosticsController {

    private final ResilientBillingJob billingJob;
    private final ShedLockSimulationService shedLockService;

    public SchedulerDiagnosticsController(ResilientBillingJob billingJob,
                                          ShedLockSimulationService shedLockService) {
        this.billingJob = billingJob;
        this.shedLockService = shedLockService;
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
                "schedulerPoolSize", 8,
                "protectedBillingExecutions", billingJob.getProtectedBillingExecutionCount(),
                "unprotectedBillingExecutions", billingJob.getUnprotectedBillingExecutionCount(),
                "reconciliationExecutions", billingJob.getReconciliationExecutionCount(),
                "shedLockStats", shedLockService.getStats()
        ));
    }

    @PostMapping("/simulate-cluster-run")
    public ResponseEntity<Map<String, Object>> simulateClusterRun(
            @RequestParam(defaultValue = "6") int clusterPodsCount,
            @RequestParam(defaultValue = "true") boolean useShedLock
    ) throws Exception {
        billingJob.reset();
        shedLockService.clear();

        ExecutorService executor = Executors.newFixedThreadPool(clusterPodsCount);
        List<Callable<Boolean>> tasks = new ArrayList<>();

        for (int i = 1; i <= clusterPodsCount; i++) {
            final String nodeId = "finflow-pod-replica-" + i;
            tasks.add(() -> {
                if (useShedLock) {
                    return billingJob.executeClusterSafeBilling(nodeId);
                } else {
                    billingJob.executeUnsafeClusterBilling(nodeId);
                    return true;
                }
            });
        }

        List<Future<Boolean>> futures = executor.invokeAll(tasks);
        for (Future<Boolean> f : futures) {
            f.get();
        }
        executor.shutdown();

        long executions = useShedLock ? billingJob.getProtectedBillingExecutionCount() : billingJob.getUnprotectedBillingExecutionCount();
        String outcome = (useShedLock && executions == 1) ? "DEDUPLICATION_SUCCESSFUL_EXACTLY_ONE_RUN" : "FATAL_DUPLICATE_CHARGES_OCCURRED";

        return ResponseEntity.ok(Map.of(
                "clusterPodsCount", clusterPodsCount,
                "useShedLock", useShedLock,
                "totalExecutionsTriggered", executions,
                "preventedDuplicateRuns", Math.max(0, clusterPodsCount - executions),
                "outcome", outcome
        ));
    }

    @PostMapping("/clear")
    public ResponseEntity<Map<String, Object>> clear() {
        billingJob.reset();
        shedLockService.clear();
        return ResponseEntity.ok(Map.of("status", "CLEARED"));
    }
}
