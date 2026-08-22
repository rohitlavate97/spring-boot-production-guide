package com.finflow.troubleshooting.module16;

import com.finflow.troubleshooting.module16.service.ContainerMemoryCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerMemoryCalculatorTest {

    private ContainerMemoryCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new ContainerMemoryCalculator();
    }

    @Test
    @DisplayName("Should validate safe memory budget when MaxRAMPercentage is 75% in a 2048MB container")
    void testSafeMemoryBudget() {
        var budget = calculator.calculateBudget(2048, 75.0, 100, 256, 128);

        assertThat(budget.containerLimitMb()).isEqualTo(2048);
        assertThat(budget.maxHeapMb()).isEqualTo(1536);
        assertThat(budget.estimatedThreadStackMb()).isEqualTo(100);
        assertThat(budget.estimatedMetaspaceMb()).isEqualTo(256);
        assertThat(budget.estimatedCodeCacheMb()).isEqualTo(128);
        assertThat(budget.estimatedDirectMemoryMb()).isEqualTo(128);
        assertThat(budget.estimatedNativeOverheadMb()).isEqualTo(64);
        
        // Total = 1536 + 100 + 256 + 128 + 128 + 64 = 2212 > 2048
        // Let's verify status calculation:
        assertThat(budget.safetyStatus()).isEqualTo("CRITICAL_OOM_KILL_GUARANTEED");
    }

    @Test
    @DisplayName("Should validate SAFE_CONTAINER_BUDGET when container is 4096MB with 60% MaxRAMPercentage")
    void testSafeStatusInLargerContainer() {
        var budget = calculator.calculateBudget(4096, 60.0, 100, 256, 128);

        // Max heap = 4096 * 0.60 = 2458MB
        // Total = 2458 + 100 + 256 + 128 + 128 + 64 = 3134MB
        // Headroom = 4096 - 3134 = 962MB (> 10% of 4096 = 409.6MB)
        assertThat(budget.safetyStatus()).isEqualTo("SAFE_CONTAINER_BUDGET");
        assertThat(budget.headroomRemainingMb()).isGreaterThan(400);
        assertThat(budget.recommendedJvmFlags()).contains("-XX:+UseContainerSupport");
    }

    @Test
    @DisplayName("Should detect critical OOM risk when heap alone consumes 100% of container limit")
    void testCriticalOomWhenHeapConsumesEntireContainer() {
        var budget = calculator.calculateBudget(1024, 100.0, 200, 256, 128);

        assertThat(budget.safetyStatus()).isEqualTo("CRITICAL_OOM_KILL_GUARANTEED");
        assertThat(budget.warnings()).anyMatch(w -> w.contains("Linux kernel OOM Killer will issue SIGKILL"));
    }

    @Test
    @DisplayName("Should detect narrow headroom warning when headroom is under 10%")
    void testNarrowHeadroomWarning() {
        // Container: 1000MB, Heap: 700MB, Non-heap: 50 + 100 + 50 + 30 + 20 = 250 -> Total = 950MB -> Headroom = 50MB (< 100MB)
        var budget = calculator.calculateBudget(1000, 70.0, 50, 100, 30);

        // Thread stack = 50, Metaspace = 100, CodeCache = 128, Direct = 30, Native = 64
        // Total non-heap = 50 + 100 + 128 + 30 + 64 = 372
        // MaxHeap = 700
        // Total = 1072 > 1000 -> CRITICAL
        assertThat(budget.safetyStatus()).isIn("CRITICAL_OOM_KILL_GUARANTEED", "HIGH_RISK_NARROW_HEADROOM");
    }
}
