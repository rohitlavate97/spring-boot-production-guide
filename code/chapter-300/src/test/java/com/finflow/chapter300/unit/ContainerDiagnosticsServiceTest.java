package com.finflow.chapter300.unit;

import com.finflow.chapter300.domain.ContainerRuntimeDiagnostics;
import com.finflow.chapter300.domain.MemoryLimitCalculation;
import com.finflow.chapter300.service.ContainerDiagnosticsService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class ContainerDiagnosticsServiceTest {

    private final ContainerDiagnosticsService service = new ContainerDiagnosticsService();

    @Test
    public void testGetDiagnostics_returnsValidRuntimeInfo() {
        ContainerRuntimeDiagnostics diagnostics = service.getDiagnostics();

        assertThat(diagnostics).isNotNull();
        assertThat(diagnostics.getAvailableProcessors()).isGreaterThan(0);
        assertThat(diagnostics.getMaxMemoryMb()).isGreaterThan(0);
        assertThat(diagnostics.getPid()).isGreaterThan(0);
        assertThat(diagnostics.getJvmVersion()).isNotEmpty();
    }

    @Test
    public void testCalculateMemoryLayout_optimal75Percent() {
        MemoryLimitCalculation calculation = service.calculateMemoryLayout(2048, 75.0);

        assertThat(calculation.getContainerLimitMb()).isEqualTo(2048);
        assertThat(calculation.getCalculatedHeapLimitMb()).isEqualTo(1536);
        assertThat(calculation.getOffHeapBufferMb()).isEqualTo(512);
        assertThat(calculation.getRecommendation()).contains("OPTIMAL");
    }

    @Test
    public void testCalculateMemoryLayout_dangerous90Percent() {
        MemoryLimitCalculation calculation = service.calculateMemoryLayout(2048, 90.0);

        assertThat(calculation.getCalculatedHeapLimitMb()).isEqualTo(1843);
        assertThat(calculation.getOffHeapBufferMb()).isEqualTo(205);
        assertThat(calculation.getRecommendation()).contains("DANGEROUS");
    }

    @Test
    public void testCalculateMemoryLayout_invalidPercentageThrows() {
        assertThatThrownBy(() -> service.calculateMemoryLayout(2048, 120.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
