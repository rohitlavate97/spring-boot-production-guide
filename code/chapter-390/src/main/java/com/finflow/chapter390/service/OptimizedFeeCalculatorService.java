package com.finflow.chapter390.service;

import com.finflow.chapter390.model.PerformanceBenchmarkReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Service comparing lock contention vs Lock-Free CAS atomics and memory allocation tuning.
 */
@Service
public class OptimizedFeeCalculatorService {

    private static final Logger log = LoggerFactory.getLogger(OptimizedFeeCalculatorService.class);

    private final Object lock = new Object();
    private long synchronizedCallCount = 0;
    private final LongAdder lockFreeCallCount = new LongAdder();

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final BigDecimal FIXED_FEE = BigDecimal.valueOf(0.30);
    private static final BigDecimal RATE_PERCENT = BigDecimal.valueOf(2.5);

    /**
     * NAIVE SYNCHRONIZED BOTTLENECK:
     * Coarse-grained synchronization on a shared singleton lock
     * creates thread serialization and severe lock contention under 200 Tomcat threads.
     */
    public BigDecimal calculateFeeSynchronized(BigDecimal amount) {
        synchronized (lock) {
            synchronizedCallCount++;
            BigDecimal percentageFee = amount.multiply(RATE_PERCENT).divide(HUNDRED, 2, RoundingMode.HALF_UP);
            return percentageFee.add(FIXED_FEE);
        }
    }

    /**
     * OPTIMIZED LOCK-FREE IMPLEMENTATION:
     * 1. Zero synchronization locks.
     * 2. LockFree LongAdder for contention-free statistics.
     * 3. Static cached BigDecimal constants to avoid heap allocations.
     */
    public BigDecimal calculateFeeLockFree(BigDecimal amount) {
        lockFreeCallCount.increment();
        BigDecimal percentageFee = amount.multiply(RATE_PERCENT).divide(HUNDRED, 2, RoundingMode.HALF_UP);
        return percentageFee.add(FIXED_FEE);
    }

    public PerformanceBenchmarkReport runBenchmark(int iterations, int concurrency) throws InterruptedException {
        log.info("[PerfBenchmark] Running benchmark with {} iterations across {} concurrent threads...",
                iterations, concurrency);

        ExecutorService executor = Executors.newFixedThreadPool(concurrency);
        BigDecimal testAmount = BigDecimal.valueOf(250.00);

        // 1. Benchmark Synchronized Method
        CountDownLatch latchSync = new CountDownLatch(iterations);
        long startSync = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            executor.submit(() -> {
                calculateFeeSynchronized(testAmount);
                latchSync.countDown();
            });
        }
        latchSync.await(30, TimeUnit.SECONDS);
        long durationSyncMs = Math.max(System.currentTimeMillis() - startSync, 1);

        // 2. Benchmark Lock-Free Method
        CountDownLatch latchLockFree = new CountDownLatch(iterations);
        long startLockFree = System.currentTimeMillis();
        for (int i = 0; i < iterations; i++) {
            executor.submit(() -> {
                calculateFeeLockFree(testAmount);
                latchLockFree.countDown();
            });
        }
        latchLockFree.await(30, TimeUnit.SECONDS);
        long durationLockFreeMs = Math.max(System.currentTimeMillis() - startLockFree, 1);

        executor.shutdown();

        double speedup = (double) durationSyncMs / durationLockFreeMs;
        double syncOps = (double) iterations / (durationSyncMs / 1000.0);
        double lockFreeOps = (double) iterations / (durationLockFreeMs / 1000.0);

        String summary = String.format(
                "Lock-Free calculation achieved %.2fx speedup (%.0f ops/sec vs %.0f ops/sec)",
                speedup, lockFreeOps, syncOps);

        log.info("[PerfBenchmark] {}", summary);

        return new PerformanceBenchmarkReport(
                iterations, concurrency, durationSyncMs, durationLockFreeMs,
                speedup, syncOps, lockFreeOps, summary
        );
    }

    public long getSynchronizedCallCount() {
        return synchronizedCallCount;
    }

    public long getLockFreeCallCount() {
        return lockFreeCallCount.sum();
    }
}
