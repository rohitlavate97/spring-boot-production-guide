package com.finflow.troubleshooting.module17.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicBoolean;

@Service("downstreamDependencyHealth")
public class SimulatedDownstreamDependencyService implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(SimulatedDownstreamDependencyService.class);

    private final AtomicBoolean databaseReachable = new AtomicBoolean(true);
    private final AtomicBoolean redisReachable = new AtomicBoolean(true);

    @Override
    public Health health() {
        if (!databaseReachable.get()) {
            return Health.down()
                    .withDetail("database", "DOWN: PostgreSQL connection pool timeout (15000ms)")
                    .withDetail("reason", "Simulated downstream database saturation")
                    .build();
        }
        if (!redisReachable.get()) {
            return Health.down()
                    .withDetail("redis", "DOWN: Redis cluster node failure")
                    .withDetail("reason", "Simulated cache node unreachable")
                    .build();
        }
        return Health.up()
                .withDetail("database", "UP: HikariCP active=4, idle=16, total=20")
                .withDetail("redis", "UP: Redis Cluster Master PING OK (0.8ms)")
                .build();
    }

    public void setDatabaseFailure(boolean failed) {
        this.databaseReachable.set(!failed);
        log.warn("Downstream PostgreSQL reachability set to: {}", !failed);
    }

    public void setRedisFailure(boolean failed) {
        this.redisReachable.set(!failed);
        log.warn("Downstream Redis reachability set to: {}", !failed);
    }

    public boolean isDatabaseReachable() {
        return databaseReachable.get();
    }

    public boolean isRedisReachable() {
        return redisReachable.get();
    }
}
