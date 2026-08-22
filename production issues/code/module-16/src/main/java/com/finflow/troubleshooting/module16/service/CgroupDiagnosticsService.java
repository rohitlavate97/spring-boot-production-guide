package com.finflow.troubleshooting.module16.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class CgroupDiagnosticsService {

    private static final Logger log = LoggerFactory.getLogger(CgroupDiagnosticsService.class);

    private final boolean simulateLimits;
    private final long simulatedMemoryLimitMb;
    private final double simulatedCpuQuotaCores;
    private final List<ByteBuffer> simulatedDirectBuffers = new CopyOnWriteArrayList<>();

    public CgroupDiagnosticsService(
            @Value("${container.diagnostics.simulate-cgroup-limits:true}") boolean simulateLimits,
            @Value("${container.diagnostics.simulated-memory-limit-mb:1024}") long simulatedMemoryLimitMb,
            @Value("${container.diagnostics.simulated-cpu-quota-cores:2.0}") double simulatedCpuQuotaCores
    ) {
        this.simulateLimits = simulateLimits;
        this.simulatedMemoryLimitMb = simulatedMemoryLimitMb;
        this.simulatedCpuQuotaCores = simulatedCpuQuotaCores;
    }

    public Map<String, Object> getCgroupAndJvmDiagnostics() {
        Map<String, Object> diagnostics = new LinkedHashMap<>();

        // 1. Cgroup Version & Detection
        String cgroupVersion = detectCgroupVersion();
        diagnostics.put("cgroupVersion", cgroupVersion);

        // 2. Cgroup Memory Limits
        long cgroupMemoryLimitBytes = readCgroupMemoryLimit(cgroupVersion);
        long cgroupMemoryLimitMb = cgroupMemoryLimitBytes > 0 ? cgroupMemoryLimitBytes / (1024 * 1024) : simulatedMemoryLimitMb;
        diagnostics.put("cgroupMemoryLimitMb", cgroupMemoryLimitMb);

        // 3. Cgroup CPU Quotas
        double cpuQuotaCores = readCgroupCpuQuota(cgroupVersion);
        if (cpuQuotaCores <= 0) {
            cpuQuotaCores = simulatedCpuQuotaCores;
        }
        diagnostics.put("cgroupCpuQuotaCores", cpuQuotaCores);

        // 4. JVM Runtime View
        Runtime runtime = Runtime.getRuntime();
        long jvmMaxMemoryMb = runtime.maxMemory() / (1024 * 1024);
        long jvmTotalMemoryMb = runtime.totalMemory() / (1024 * 1024);
        long jvmFreeMemoryMb = runtime.freeMemory() / (1024 * 1024);
        long jvmUsedMemoryMb = jvmTotalMemoryMb - jvmFreeMemoryMb;
        int availableProcessors = runtime.availableProcessors();

        diagnostics.put("jvmAvailableProcessors", availableProcessors);
        diagnostics.put("jvmMaxHeapMb", jvmMaxMemoryMb);
        diagnostics.put("jvmTotalHeapMb", jvmTotalMemoryMb);
        diagnostics.put("jvmUsedHeapMb", jvmUsedMemoryMb);
        diagnostics.put("jvmFreeHeapMb", jvmFreeMemoryMb);

        // 5. Non-Heap & Direct Buffers
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        long nonHeapUsedMb = memoryBean.getNonHeapMemoryUsage().getUsed() / (1024 * 1024);
        long nonHeapCommittedMb = memoryBean.getNonHeapMemoryUsage().getCommitted() / (1024 * 1024);
        diagnostics.put("jvmNonHeapUsedMb", nonHeapUsedMb);
        diagnostics.put("jvmNonHeapCommittedMb", nonHeapCommittedMb);

        long directBufferUsedMb = getDirectBufferMemoryUsedBytes() / (1024 * 1024);
        diagnostics.put("jvmDirectBufferMemoryUsedMb", directBufferUsedMb);
        diagnostics.put("simulatedDirectBuffersAllocatedMb", simulatedDirectBuffers.size() * 10L);

        // 6. Total Estimated JVM Process Footprint
        long totalEstimatedProcessFootprintMb = jvmTotalMemoryMb + nonHeapCommittedMb + directBufferUsedMb;
        diagnostics.put("totalEstimatedProcessFootprintMb", totalEstimatedProcessFootprintMb);
        long containerHeadroomMb = cgroupMemoryLimitMb - totalEstimatedProcessFootprintMb;
        diagnostics.put("containerHeadroomMb", containerHeadroomMb);

        // 7. Safety Status Check
        if (totalEstimatedProcessFootprintMb >= cgroupMemoryLimitMb) {
            diagnostics.put("containerStatus", "CRITICAL_RISK_OOM_KILL");
        } else if (containerHeadroomMb < (cgroupMemoryLimitMb * 0.15)) {
            diagnostics.put("containerStatus", "WARNING_LOW_HEADROOM");
        } else {
            diagnostics.put("containerStatus", "HEALTHY");
        }

        return diagnostics;
    }

    public void allocateDirectMemoryMb(int megabytes) {
        int chunks = megabytes / 10;
        if (chunks <= 0) chunks = 1;
        for (int i = 0; i < chunks; i++) {
            ByteBuffer buffer = ByteBuffer.allocateDirect(10 * 1024 * 1024); // 10MB chunk
            simulatedDirectBuffers.add(buffer);
        }
        log.info("Allocated {}MB DirectByteBuffer off-heap memory. Total simulated chunks: {}", chunks * 10, simulatedDirectBuffers.size());
    }

    public void clearDirectMemory() {
        simulatedDirectBuffers.clear();
        log.info("Cleared simulated off-heap direct byte buffers.");
    }

    public long getSimulatedDirectMemoryMb() {
        return simulatedDirectBuffers.size() * 10L;
    }

    public String detectCgroupVersion() {
        if (new File("/sys/fs/cgroup/cgroup.controllers").exists()) {
            return "v2";
        } else if (new File("/sys/fs/cgroup/memory").exists() || new File("/sys/fs/cgroup/cpu").exists()) {
            return "v1";
        }
        return "HOST_OR_SIMULATED";
    }

    public long readCgroupMemoryLimit(String cgroupVersion) {
        try {
            if ("v2".equals(cgroupVersion)) {
                Path path = Path.of("/sys/fs/cgroup/memory.max");
                if (Files.exists(path)) {
                    String val = Files.readString(path).trim();
                    if (!"max".equalsIgnoreCase(val)) {
                        return Long.parseLong(val);
                    }
                }
            } else if ("v1".equals(cgroupVersion)) {
                Path path = Path.of("/sys/fs/cgroup/memory/memory.limit_in_bytes");
                if (Files.exists(path)) {
                    String val = Files.readString(path).trim();
                    return Long.parseLong(val);
                }
            }
        } catch (Exception ex) {
            log.warn("Unable to read cgroup memory limit: {}", ex.getMessage());
        }
        return simulateLimits ? (simulatedMemoryLimitMb * 1024 * 1024) : -1;
    }

    public double readCgroupCpuQuota(String cgroupVersion) {
        try {
            if ("v2".equals(cgroupVersion)) {
                Path path = Path.of("/sys/fs/cgroup/cpu.max");
                if (Files.exists(path)) {
                    String[] tokens = Files.readString(path).trim().split("\\s+");
                    if (tokens.length == 2 && !"max".equalsIgnoreCase(tokens[0])) {
                        double quota = Double.parseDouble(tokens[0]);
                        double period = Double.parseDouble(tokens[1]);
                        return quota / period;
                    }
                }
            } else if ("v1".equals(cgroupVersion)) {
                Path quotaPath = Path.of("/sys/fs/cgroup/cpu/cpu.cfs_quota_us");
                Path periodPath = Path.of("/sys/fs/cgroup/cpu/cpu.cfs_period_us");
                if (Files.exists(quotaPath) && Files.exists(periodPath)) {
                    double quota = Double.parseDouble(Files.readString(quotaPath).trim());
                    double period = Double.parseDouble(Files.readString(periodPath).trim());
                    if (quota > 0 && period > 0) {
                        return quota / period;
                    }
                }
            }
        } catch (Exception ex) {
            log.warn("Unable to read cgroup cpu quota: {}", ex.getMessage());
        }
        return simulateLimits ? simulatedCpuQuotaCores : -1;
    }

    private long getDirectBufferMemoryUsedBytes() {
        List<BufferPoolMXBean> pools = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class);
        for (BufferPoolMXBean pool : pools) {
            if ("direct".equalsIgnoreCase(pool.getName())) {
                return pool.getMemoryUsed();
            }
        }
        return 0;
    }
}
