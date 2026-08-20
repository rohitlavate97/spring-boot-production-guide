package com.finflow.chapter300.service;

import com.finflow.chapter300.domain.ContainerRuntimeDiagnostics;
import com.finflow.chapter300.domain.MemoryLimitCalculation;
import org.springframework.stereotype.Service;

@Service
public class ContainerDiagnosticsService {

    private static final long MB = 1024 * 1024;

    public ContainerRuntimeDiagnostics getDiagnostics() {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / MB;
        long totalMemory = runtime.totalMemory() / MB;
        long freeMemory = runtime.freeMemory() / MB;
        long usedMemory = totalMemory - freeMemory;

        return new ContainerRuntimeDiagnostics(
                runtime.availableProcessors(),
                maxMemory,
                totalMemory,
                freeMemory,
                usedMemory,
                System.getProperty("java.version"),
                System.getProperty("os.name"),
                ProcessHandle.current().pid()
        );
    }

    public MemoryLimitCalculation calculateMemoryLayout(long containerLimitMb, double maxRamPercentage) {
        if (maxRamPercentage <= 0.0 || maxRamPercentage > 100.0) {
            throw new IllegalArgumentException("maxRamPercentage must be between 0.0 and 100.0");
        }

        long heapLimit = Math.round(containerLimitMb * (maxRamPercentage / 100.0));
        long offHeapBuffer = containerLimitMb - heapLimit;

        String recommendation;
        if (maxRamPercentage > 80.0) {
            recommendation = "DANGEROUS: Heap percentage > 80% risks Linux OOMKilled 137 due to Metaspace, DirectMemory, and Thread Stacks.";
        } else if (maxRamPercentage < 50.0) {
            recommendation = "SUBOPTIMAL: Heap percentage < 50% leaves excessive unused RAM buffer.";
        } else {
            recommendation = "OPTIMAL: 70-75% RAM allocation safely buffers off-heap allocations.";
        }

        return new MemoryLimitCalculation(
                containerLimitMb,
                maxRamPercentage,
                heapLimit,
                offHeapBuffer,
                recommendation
        );
    }
}
