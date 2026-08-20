package com.finflow.chapter190.unit;

import com.finflow.chapter190.Chapter190Application;
import com.finflow.chapter190.correct.HikariPoolMonitoringService;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter190Application.class)
public class HikariPoolConfigurationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private HikariPoolMonitoringService monitoringService;

    @Test
    public void testHikariDataSource_initializedWithProductionDefaults() {
        assertThat(dataSource).isInstanceOf(HikariDataSource.class);
        HikariDataSource hds = (HikariDataSource) dataSource;

        // Verify Fixed-Size Pool: maximum-pool-size == minimum-idle
        assertThat(hds.getMaximumPoolSize()).isEqualTo(10);
        assertThat(hds.getMinimumIdle()).isEqualTo(10);

        // Verify Pool Name & Lifecycle timeouts
        assertThat(hds.getPoolName()).isEqualTo("FinFlowHikariPool");
        assertThat(hds.getConnectionTimeout()).isEqualTo(30000);
        assertThat(hds.getMaxLifetime()).isEqualTo(1800000);
        assertThat(hds.getLeakDetectionThreshold()).isEqualTo(2000);
        assertThat(hds.getValidationTimeout()).isEqualTo(250);

        assertThat(monitoringService.getMaximumPoolSize()).isEqualTo(10);
        assertThat(monitoringService.getMinimumIdle()).isEqualTo(10);
    }
}
