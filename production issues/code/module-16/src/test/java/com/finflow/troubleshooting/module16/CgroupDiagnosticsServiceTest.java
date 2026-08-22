package com.finflow.troubleshooting.module16;

import com.finflow.troubleshooting.module16.service.CgroupDiagnosticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CgroupDiagnosticsServiceTest {

    private CgroupDiagnosticsService service;

    @BeforeEach
    void setUp() {
        // simulateLimits = true, simulatedMemoryLimitMb = 1024, simulatedCpuQuotaCores = 2.0
        service = new CgroupDiagnosticsService(true, 1024, 2.0);
    }

    @Test
    @DisplayName("Should detect cgroup diagnostics structure and memory limits")
    void testGetCgroupAndJvmDiagnostics() {
        Map<String, Object> diag = service.getCgroupAndJvmDiagnostics();

        assertThat(diag).containsKey("cgroupVersion");
        assertThat(diag).containsKey("cgroupMemoryLimitMb");
        assertThat(diag).containsKey("cgroupCpuQuotaCores");
        assertThat(diag).containsKey("jvmAvailableProcessors");
        assertThat(diag).containsKey("jvmMaxHeapMb");
        assertThat(diag).containsKey("totalEstimatedProcessFootprintMb");
        assertThat(diag).containsKey("containerStatus");

        assertThat(((Number) diag.get("cgroupMemoryLimitMb")).longValue()).isGreaterThan(0);
        assertThat(((Number) diag.get("cgroupCpuQuotaCores")).doubleValue()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Should allocate and clear simulated direct off-heap memory")
    void testDirectMemoryAllocation() {
        assertThat(service.getSimulatedDirectMemoryMb()).isEqualTo(0);

        service.allocateDirectMemoryMb(30);
        assertThat(service.getSimulatedDirectMemoryMb()).isEqualTo(30);

        service.clearDirectMemory();
        assertThat(service.getSimulatedDirectMemoryMb()).isEqualTo(0);
    }

    @Test
    @DisplayName("Should read simulated memory and cpu quotas correctly")
    void testSimulatedLimits() {
        String ver = service.detectCgroupVersion();
        assertThat(ver).isNotEmpty();

        long memLimit = service.readCgroupMemoryLimit(ver);
        assertThat(memLimit).isGreaterThan(0);

        double cpuQuota = service.readCgroupCpuQuota(ver);
        assertThat(cpuQuota).isGreaterThan(0);
    }
}
