package com.finflow.chapter310.domain;

import java.io.Serializable;

public class PodHealthState implements Serializable {

    private String podName;
    private String livenessStatus;
    private String readinessStatus;
    private int activeConnections;
    private long uptimeSeconds;

    public PodHealthState() {}

    public PodHealthState(String podName, String livenessStatus, String readinessStatus,
                          int activeConnections, long uptimeSeconds) {
        this.podName = podName;
        this.livenessStatus = livenessStatus;
        this.readinessStatus = readinessStatus;
        this.activeConnections = activeConnections;
        this.uptimeSeconds = uptimeSeconds;
    }

    public String getPodName() { return podName; }
    public void setPodName(String podName) { this.podName = podName; }
    public String getLivenessStatus() { return livenessStatus; }
    public void setLivenessStatus(String livenessStatus) { this.livenessStatus = livenessStatus; }
    public String getReadinessStatus() { return readinessStatus; }
    public void setReadinessStatus(String readinessStatus) { this.readinessStatus = readinessStatus; }
    public int getActiveConnections() { return activeConnections; }
    public void setActiveConnections(int activeConnections) { this.activeConnections = activeConnections; }
    public long getUptimeSeconds() { return uptimeSeconds; }
    public void setUptimeSeconds(long uptimeSeconds) { this.uptimeSeconds = uptimeSeconds; }
}
