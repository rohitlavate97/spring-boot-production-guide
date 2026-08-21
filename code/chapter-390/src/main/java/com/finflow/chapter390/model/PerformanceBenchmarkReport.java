package com.finflow.chapter390.model;

import java.time.Instant;

public class PerformanceBenchmarkReport {

    private int iterations;
    private int concurrency;
    private long synchronizedDurationMs;
    private long lockFreeDurationMs;
    private double speedupFactor;
    private double synchronizedOpsPerSec;
    private double lockFreeOpsPerSec;
    private String optimizationSummary;
    private Instant executedAt;

    public PerformanceBenchmarkReport() {
        this.executedAt = Instant.now();
    }

    public PerformanceBenchmarkReport(int iterations, int concurrency, long synchronizedDurationMs,
                                      long lockFreeDurationMs, double speedupFactor,
                                      double synchronizedOpsPerSec, double lockFreeOpsPerSec,
                                      String optimizationSummary) {
        this.iterations = iterations;
        this.concurrency = concurrency;
        this.synchronizedDurationMs = synchronizedDurationMs;
        this.lockFreeDurationMs = lockFreeDurationMs;
        this.speedupFactor = speedupFactor;
        this.synchronizedOpsPerSec = synchronizedOpsPerSec;
        this.lockFreeOpsPerSec = lockFreeOpsPerSec;
        this.optimizationSummary = optimizationSummary;
        this.executedAt = Instant.now();
    }

    public int getIterations() {
        return iterations;
    }

    public int getConcurrency() {
        return concurrency;
    }

    public long getSynchronizedDurationMs() {
        return synchronizedDurationMs;
    }

    public long getLockFreeDurationMs() {
        return lockFreeDurationMs;
    }

    public double getSpeedupFactor() {
        return speedupFactor;
    }

    public double getSynchronizedOpsPerSec() {
        return synchronizedOpsPerSec;
    }

    public double getLockFreeOpsPerSec() {
        return lockFreeOpsPerSec;
    }

    public String getOptimizationSummary() {
        return optimizationSummary;
    }

    public Instant getExecutedAt() {
        return executedAt;
    }
}
