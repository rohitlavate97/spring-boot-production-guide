package com.finflow.chapter390.service;

import com.finflow.chapter390.model.GcInfoSnapshot;
import org.springframework.stereotype.Service;

import java.lang.management.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class GcInfoCollectorService {

    public GcInfoSnapshot collectGcInfo() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        String collectorName = "Unknown";
        long totalCount = 0;
        long totalTimeMs = 0;

        StringBuilder nameBuilder = new StringBuilder();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            nameBuilder.append(gcBean.getName()).append("; ");
            if (gcBean.getCollectionCount() > 0) {
                totalCount += gcBean.getCollectionCount();
            }
            if (gcBean.getCollectionTime() > 0) {
                totalTimeMs += gcBean.getCollectionTime();
            }
        }
        if (nameBuilder.length() > 0) {
            collectorName = nameBuilder.toString();
        }

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heapUsage = memoryMXBean.getHeapMemoryUsage();
        MemoryUsage nonHeapUsage = memoryMXBean.getNonHeapMemoryUsage();

        long heapUsedMb = heapUsage.getUsed() / (1024 * 1024);
        long heapMaxMb = heapUsage.getMax() / (1024 * 1024);
        long nonHeapUsedMb = nonHeapUsage.getUsed() / (1024 * 1024);

        Map<String, Long> poolUsage = new HashMap<>();
        for (MemoryPoolMXBean poolBean : ManagementFactory.getMemoryPoolMXBeans()) {
            poolUsage.put(poolBean.getName(), poolBean.getUsage().getUsed() / (1024 * 1024));
        }

        return new GcInfoSnapshot(
                collectorName,
                totalCount,
                totalTimeMs,
                heapUsedMb,
                heapMaxMb,
                nonHeapUsedMb,
                poolUsage
        );
    }
}
