package com.finflow.chapter190.dto;

public record PoolMetricsSnapshot(
        String poolName,
        int activeConnections,
        int idleConnections,
        int totalConnections,
        int threadsAwaitingConnection,
        boolean isHealthy
) {
}
