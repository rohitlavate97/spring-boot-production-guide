package com.finflow.chapter380.model;

import java.time.Instant;
import java.util.Map;

public class DiagnosticSnapshot {

    private int activeThreadCount;
    private long freeMemoryMb;
    private long totalMemoryMb;
    private long maxMemoryMb;
    private double currentErrorRatePercent;
    private double p99LatencyMs;
    private Map<String, String> activeAlerts;
    private Instant timestamp;

    public DiagnosticSnapshot() {
        this.timestamp = Instant.now();
    }

    public DiagnosticSnapshot(int activeThreadCount, long freeMemoryMb, long totalMemoryMb,
                              long maxMemoryMb, double currentErrorRatePercent, double p99LatencyMs,
                              Map<String, String> activeAlerts) {
        this.activeThreadCount = activeThreadCount;
        this.freeMemoryMb = freeMemoryMb;
        this.totalMemoryMb = totalMemoryMb;
        this.maxMemoryMb = maxMemoryMb;
        this.currentErrorRatePercent = currentErrorRatePercent;
        this.p99LatencyMs = p99LatencyMs;
        this.activeAlerts = activeAlerts;
        this.timestamp = Instant.now();
    }

    public int getActiveThreadCount() {
        return activeThreadCount;
    }

    public long getFreeMemoryMb() {
        return freeMemoryMb;
    }

    public long getTotalMemoryMb() {
        return totalMemoryMb;
    }

    public long getMaxMemoryMb() {
        return maxMemoryMb;
    }

    public double getCurrentErrorRatePercent() {
        return currentErrorRatePercent;
    }

    public double getP99LatencyMs() {
        return p99LatencyMs;
    }

    public Map<String, String> getActiveAlerts() {
        return activeAlerts;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
