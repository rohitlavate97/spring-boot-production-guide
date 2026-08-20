package com.finflow.chapter190.unit;

import com.finflow.chapter190.Chapter190Application;
import com.finflow.chapter190.correct.HikariPoolMonitoringService;
import com.finflow.chapter190.dto.PoolMetricsSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter190Application.class)
public class PoolMetricsSnapshotTest {

    @Autowired
    private HikariPoolMonitoringService monitoringService;

    @Test
    public void testPoolMetricsSnapshot_returnsAccurateGauges() {
        PoolMetricsSnapshot snapshot = monitoringService.getPoolSnapshot();

        assertThat(snapshot).isNotNull();
        assertThat(snapshot.poolName()).isEqualTo("FinFlowHikariPool");
        assertThat(snapshot.totalConnections()).isEqualTo(10);
        assertThat(snapshot.threadsAwaitingConnection()).isEqualTo(0);
        assertThat(snapshot.isHealthy()).isTrue();
    }
}
