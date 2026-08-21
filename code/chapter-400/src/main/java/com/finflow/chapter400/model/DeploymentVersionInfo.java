package com.finflow.chapter400.model;

import java.time.Instant;
import java.util.Map;

public class DeploymentVersionInfo {

    private String version;
    private String environment;
    private String strategy;
    private String commitSha;
    private int activeReplicaCount;
    private Map<String, Boolean> activeFeatureFlags;
    private Instant deployedAt;

    public DeploymentVersionInfo() {
        this.deployedAt = Instant.now();
    }

    public DeploymentVersionInfo(String version, String environment, String strategy,
                                 String commitSha, int activeReplicaCount,
                                 Map<String, Boolean> activeFeatureFlags) {
        this.version = version;
        this.environment = environment;
        this.strategy = strategy;
        this.commitSha = commitSha;
        this.activeReplicaCount = activeReplicaCount;
        this.activeFeatureFlags = activeFeatureFlags;
        this.deployedAt = Instant.now();
    }

    public String getVersion() {
        return version;
    }

    public String getEnvironment() {
        return environment;
    }

    public String getStrategy() {
        return strategy;
    }

    public String getCommitSha() {
        return commitSha;
    }

    public int getActiveReplicaCount() {
        return activeReplicaCount;
    }

    public Map<String, Boolean> getActiveFeatureFlags() {
        return activeFeatureFlags;
    }

    public Instant getDeployedAt() {
        return deployedAt;
    }
}
