package com.finflow.troubleshooting.module16.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ContainerMemoryCalculator {

    public record MemoryBudgetResult(
            long containerLimitMb,
            double maxRamPercentage,
            long maxHeapMb,
            long estimatedThreadStackMb,
            long estimatedMetaspaceMb,
            long estimatedCodeCacheMb,
            long estimatedDirectMemoryMb,
            long estimatedNativeOverheadMb,
            long totalEstimatedJvmProcessMemoryMb,
            long headroomRemainingMb,
            String safetyStatus,
            List<String> warnings,
            List<String> recommendedJvmFlags
    ) {}

    public MemoryBudgetResult calculateBudget(
            long containerLimitMb,
            double maxRamPercentage,
            int threadCount,
            long metaspaceMb,
            long directMemoryMb
    ) {
        long codeCacheMb = 128;
        long threadStackMb = (long) Math.ceil((threadCount * 1024L) / 1024.0); // 1MB per thread stack default (-Xss1m)
        long nativeOverheadMb = 64; // JVM internal accounting, symbols, GC structures

        long maxHeapMb = Math.round(containerLimitMb * (maxRamPercentage / 100.0));
        long totalJvmMemoryMb = maxHeapMb + threadStackMb + metaspaceMb + codeCacheMb + directMemoryMb + nativeOverheadMb;
        long headroomMb = containerLimitMb - totalJvmMemoryMb;

        List<String> warnings = new ArrayList<>();
        List<String> flags = new ArrayList<>();
        String safetyStatus;

        if (totalJvmMemoryMb > containerLimitMb) {
            safetyStatus = "CRITICAL_OOM_KILL_GUARANTEED";
            warnings.add("Total projected JVM memory (" + totalJvmMemoryMb + "MB) EXCEEDS container cgroup limit (" 
                    + containerLimitMb + "MB) by " + Math.abs(headroomMb) + "MB! Linux kernel OOM Killer will issue SIGKILL (Exit 137).");
        } else if (headroomMb < (containerLimitMb * 0.10)) {
            safetyStatus = "HIGH_RISK_NARROW_HEADROOM";
            warnings.add("Headroom (" + headroomMb + "MB) is under 10% of container limit. Native allocations (glibc malloc arenas, compression, TLS) risk triggering OOM Killer.");
        } else {
            safetyStatus = "SAFE_CONTAINER_BUDGET";
        }

        if (maxRamPercentage > 80.0) {
            warnings.add("MaxRAMPercentage of " + maxRamPercentage + "% is too aggressive for small containers (<2GB). Recommended is 70.0% to 75.0%.");
        }

        // Recommend hardened JVM container flags
        flags.add("-XX:+UseContainerSupport");
        flags.add("-XX:MaxRAMPercentage=" + (maxRamPercentage > 75.0 ? "75.0" : maxRamPercentage));
        flags.add("-XX:InitialRAMPercentage=" + (maxRamPercentage > 75.0 ? "75.0" : maxRamPercentage));
        flags.add("-XX:MaxMetaspaceSize=" + metaspaceMb + "m");
        flags.add("-XX:ReservedCodeCacheSize=" + codeCacheMb + "m");
        flags.add("-XX:MaxDirectMemorySize=" + directMemoryMb + "m");
        flags.add("-XX:+ExitOnOutOfMemoryError");
        flags.add("-XX:+HeapDumpOnOutOfMemoryError");
        flags.add("-XX:HeapDumpPath=/tmp/dumps/oom.hprof");

        return new MemoryBudgetResult(
                containerLimitMb,
                maxRamPercentage,
                maxHeapMb,
                threadStackMb,
                metaspaceMb,
                codeCacheMb,
                directMemoryMb,
                nativeOverheadMb,
                totalJvmMemoryMb,
                headroomMb,
                safetyStatus,
                warnings,
                flags
        );
    }
}
