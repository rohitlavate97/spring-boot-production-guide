package com.finflow.chapter190.correct;

import com.finflow.chapter190.dto.PoolMetricsSnapshot;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;

@Service
public class HikariPoolMonitoringService {

    private final HikariDataSource hikariDataSource;
    private final MeterRegistry meterRegistry;

    public HikariPoolMonitoringService(DataSource dataSource, MeterRegistry meterRegistry) {
        if (dataSource instanceof HikariDataSource hds) {
            this.hikariDataSource = hds;
        } else {
            this.hikariDataSource = null;
        }
        this.meterRegistry = meterRegistry;
    }

    /**
     * Captures a point-in-time snapshot of the HikariCP connection pool.
     */
    public PoolMetricsSnapshot getPoolSnapshot() {
        if (hikariDataSource == null) {
            return new PoolMetricsSnapshot("UNKNOWN", 0, 0, 0, 0, false);
        }

        HikariPoolMXBean poolMxBean = hikariDataSource.getHikariPoolMXBean();
        if (poolMxBean == null) {
            return new PoolMetricsSnapshot(hikariDataSource.getPoolName(), 0, 0, 0, 0, true);
        }

        int active = poolMxBean.getActiveConnections();
        int idle = poolMxBean.getIdleConnections();
        int total = poolMxBean.getTotalConnections();
        int waiting = poolMxBean.getThreadsAwaitingConnection();

        // Consider pool unhealthy if threads are blocked waiting for connections
        boolean healthy = waiting == 0;

        return new PoolMetricsSnapshot(
                hikariDataSource.getPoolName(),
                active,
                idle,
                total,
                waiting,
                healthy
        );
    }

    public int getMaximumPoolSize() {
        return hikariDataSource != null ? hikariDataSource.getMaximumPoolSize() : 0;
    }

    public int getMinimumIdle() {
        return hikariDataSource != null ? hikariDataSource.getMinimumIdle() : 0;
    }
}
