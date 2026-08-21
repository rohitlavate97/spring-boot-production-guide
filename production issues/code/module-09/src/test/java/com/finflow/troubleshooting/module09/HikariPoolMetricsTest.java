package com.finflow.troubleshooting.module09;

import com.finflow.troubleshooting.module09.service.HikariPoolMetricsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module09Application.class)
public class HikariPoolMetricsTest {

    @Autowired
    private HikariPoolMetricsService metricsService;

    @Test
    void testHikariPoolMetricsReportAccurateConfiguration() {
        Map<String, Object> stats = metricsService.getPoolStatistics();

        assertThat(stats.get("poolName")).isEqualTo("FinFlowHikariPool");
        assertThat(stats.get("maxPoolSize")).isEqualTo(3);
        assertThat((Integer) stats.get("totalConnections")).isGreaterThanOrEqualTo(1);
    }
}
