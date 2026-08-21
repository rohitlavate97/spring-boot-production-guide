package com.finflow.troubleshooting.module09.service;

import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.Map;

@Service
public class HikariPoolMetricsService {

    private final DataSource dataSource;

    public HikariPoolMetricsService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, Object> getPoolStatistics() {
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            HikariPoolMXBean poolBean = hikariDataSource.getHikariPoolMXBean();
            if (poolBean != null) {
                return Map.of(
                        "poolName", hikariDataSource.getPoolName(),
                        "activeConnections", poolBean.getActiveConnections(),
                        "idleConnections", poolBean.getIdleConnections(),
                        "totalConnections", poolBean.getTotalConnections(),
                        "threadsAwaitingConnection", poolBean.getThreadsAwaitingConnection(),
                        "maxPoolSize", hikariDataSource.getMaximumPoolSize(),
                        "connectionTimeoutMs", hikariDataSource.getConnectionTimeout()
                );
            }
        }
        return Map.of("status", "HikariPoolMXBean unavailable");
    }

    public int getActiveCount() {
        if (dataSource instanceof HikariDataSource hikariDataSource && hikariDataSource.getHikariPoolMXBean() != null) {
            return hikariDataSource.getHikariPoolMXBean().getActiveConnections();
        }
        return -1;
    }
}
