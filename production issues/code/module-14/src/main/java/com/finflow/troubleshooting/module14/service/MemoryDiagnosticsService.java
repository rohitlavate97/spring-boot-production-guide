package com.finflow.troubleshooting.module14.service;

import org.springframework.stereotype.Service;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class MemoryDiagnosticsService {

    public Map<String, Object> getMemoryStatistics() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();

        Map<String, Object> stats = new HashMap<>();
        stats.put("heapUsedBytes", heapUsage.getUsed());
        stats.put("heapCommittedBytes", heapUsage.getCommitted());
        stats.put("heapMaxBytes", heapUsage.getMax());
        stats.put("nonHeapUsedBytes", nonHeapUsage.getUsed());
        stats.put("nonHeapCommittedBytes", nonHeapUsage.getCommitted());

        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        long totalGcCount = 0;
        long totalGcTimeMs = 0;
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            long count = gcBean.getCollectionCount();
            long time = gcBean.getCollectionTime();
            if (count > 0) totalGcCount += count;
            if (time > 0) totalGcTimeMs += time;
        }

        stats.put("totalGcCount", totalGcCount);
        stats.put("totalGcTimeMs", totalGcTimeMs);
        return stats;
    }
}
