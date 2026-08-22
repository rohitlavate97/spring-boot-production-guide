package com.finflow.troubleshooting.module22.job;

import com.finflow.troubleshooting.module22.service.ShedLockSimulationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class ResilientBillingJob {

    private static final Logger log = LoggerFactory.getLogger(ResilientBillingJob.class);

    private final ShedLockSimulationService shedLockService;

    private final AtomicLong protectedBillingExecutionCount = new AtomicLong(0);
    private final AtomicLong unprotectedBillingExecutionCount = new AtomicLong(0);
    private final AtomicLong reconciliationExecutionCount = new AtomicLong(0);

    public ResilientBillingJob(ShedLockSimulationService shedLockService) {
        this.shedLockService = shedLockService;
    }

    /**
     * ✅ PRODUCTION PATTERN: Cluster-Safe Scheduled Job with Distributed Lock & UTC Timezone
     * In real deployments, uses @SchedulerLock(name = "MonthlyBillingJob", lockAtMostFor = "30m", lockAtLeastFor = "5m")
     */
    @Scheduled(cron = "0 0 1 1 * ?", zone = "UTC")
    public void executeMonthlyBillingCron() {
        executeClusterSafeBilling("current-pod-node-1");
    }

    public boolean executeClusterSafeBilling(String nodeId) {
        return shedLockService.executeWithLock("MonthlyBillingJob", nodeId, 1800000, 300000, () -> {
            protectedBillingExecutionCount.incrementAndGet();
            log.info("[MONTHLY BILLING SUCCESS] Node {} processed subscription billing for 40,000 accounts.", nodeId);
        });
    }

    /**
     * ❌ ANTI-PATTERN: Unprotected Scheduled Job in Multi-Node Cluster
     * Fired by every replica pod simultaneously!
     */
    public void executeUnsafeClusterBilling(String nodeId) {
        unprotectedBillingExecutionCount.incrementAndGet();
        log.warn("[DUPLICATE BILLING ALERT] Node {} charged 40,000 customer accounts! (Total charges across cluster: {})",
                nodeId, unprotectedBillingExecutionCount.get());
    }

    /**
     * ✅ PRODUCTION PATTERN: fixedDelay (Guarantees zero concurrent execution overlap)
     */
    @Scheduled(fixedDelay = 60000)
    public void executeReconciliationFixedDelay() {
        reconciliationExecutionCount.incrementAndGet();
    }

    public long getProtectedBillingExecutionCount() {
        return protectedBillingExecutionCount.get();
    }

    public long getUnprotectedBillingExecutionCount() {
        return unprotectedBillingExecutionCount.get();
    }

    public long getReconciliationExecutionCount() {
        return reconciliationExecutionCount.get();
    }

    public void reset() {
        protectedBillingExecutionCount.set(0);
        unprotectedBillingExecutionCount.set(0);
        reconciliationExecutionCount.set(0);
    }
}
