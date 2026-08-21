package com.finflow.chapter390.model;

import java.time.Instant;
import java.util.Map;

public class GcInfoSnapshot {

    private String collectorName;
    private long totalCollectionCount;
    private long totalCollectionTimeMs;
    private long heapUsedMb;
    private long heapMaxMb;
    private long nonHeapUsedMb;
    private Map<String, Long> memoryPoolUsageMb;
    private Instant timestamp;

    public GcInfoSnapshot() {
        this.timestamp = Instant.now();
    }

    public GcInfoSnapshot(String collectorName, long totalCollectionCount, long totalCollectionTimeMs,
                          long heapUsedMb, long heapMaxMb, long nonHeapUsedMb,
                          Map<String, Long> memoryPoolUsageMb) {
        this.collectorName = collectorName;
        this.totalCollectionCount = totalCollectionCount;
        this.totalCollectionTimeMs = totalCollectionTimeMs;
        this.heapUsedMb = heapUsedMb;
        this.heapMaxMb = heapMaxMb;
        this.nonHeapUsedMb = nonHeapUsedMb;
        this.memoryPoolUsageMb = memoryPoolUsageMb;
        this.timestamp = Instant.now();
    }

    public String getCollectorName() {
        return collectorName;
    }

    public long getTotalCollectionCount() {
        return totalCollectionCount;
    }

    public long getTotalCollectionTimeMs() {
        return totalCollectionTimeMs;
    }

    public long getHeapUsedMb() {
        return heapUsedMb;
    }

    public long getHeapMaxMb() {
        return heapMaxMb;
    }

    public long getNonHeapUsedMb() {
        return nonHeapUsedMb;
    }

    public Map<String, Long> getMemoryPoolUsageMb() {
        return memoryPoolUsageMb;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}
