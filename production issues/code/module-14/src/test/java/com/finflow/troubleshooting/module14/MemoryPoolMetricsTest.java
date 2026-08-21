package com.finflow.troubleshooting.module14;

import com.finflow.troubleshooting.module14.service.MemoryDiagnosticsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module14Application.class)
public class MemoryPoolMetricsTest {

    @Autowired
    private MemoryDiagnosticsService memoryService;

    @Test
    void testMemoryStatisticsReturnsValidHeapAndNonHeapTelemetry() {
        Map<String, Object> stats = memoryService.getMemoryStatistics();

        assertThat(stats).containsKey("heapUsedBytes");
        assertThat(stats).containsKey("heapCommittedBytes");
        assertThat(stats).containsKey("nonHeapUsedBytes");

        long heapUsed = (long) stats.get("heapUsedBytes");
        long nonHeapUsed = (long) stats.get("nonHeapUsedBytes");

        assertThat(heapUsed).isGreaterThan(0L);
        assertThat(nonHeapUsed).isGreaterThan(0L);
    }
}
