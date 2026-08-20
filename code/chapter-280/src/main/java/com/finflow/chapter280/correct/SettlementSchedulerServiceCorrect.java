package com.finflow.chapter280.correct;

import com.finflow.chapter280.domain.SettlementBatch;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class SettlementSchedulerServiceCorrect {

    private static final Logger log = LoggerFactory.getLogger(SettlementSchedulerServiceCorrect.class);

    private final AtomicInteger executionCount = new AtomicInteger(0);

    /**
     * Hardened Daily Settlement Job:
     * 1. @Scheduled runs cron daily at 02:00:00 UTC.
     * 2. @SchedulerLock ensures across all Kubernetes pods, EXACTLY ONE POD acquires the lock
     *    in the shedlock table and executes the job.
     * 3. lockAtMostFor = "PT15M" ensures if the pod crashes mid-settlement, the lock is released
     *    automatically after 15 minutes, preventing permanent deadlocks.
     * 4. lockAtLeastFor = "PT5S" ensures fast execution does not release the lock before other pods
     *    finish checking the cron trigger window, preventing duplicate executions due to pod clock skew.
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @SchedulerLock(name = "dailySettlementTask", lockAtMostFor = "PT15M", lockAtLeastFor = "PT5S")
    public SettlementBatch executeDailySettlement() {
        log.info("ShedLock acquired. Executing daily settlement batch on thread: {}", Thread.currentThread().getName());
        executionCount.incrementAndGet();

        // Simulate batch computation
        return new SettlementBatch(
                "BATCH-" + UUID.randomUUID().toString().substring(0, 8),
                LocalDate.now().toString(),
                1250,
                BigDecimal.valueOf(14_250_000.00),
                "COMPLETED",
                "pod-payment-settler-1",
                Instant.now(),
                Instant.now()
        );
    }

    public int getExecutionCount() {
        return executionCount.get();
    }

    public void reset() {
        executionCount.set(0);
    }
}
