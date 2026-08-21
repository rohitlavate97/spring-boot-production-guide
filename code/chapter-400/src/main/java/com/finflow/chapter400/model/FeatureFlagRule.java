package com.finflow.chapter400.model;

import java.util.Set;

public class FeatureFlagRule {

    private String flagKey;
    private boolean enabled;
    private int rolloutPercentage; // 0 to 100
    private Set<String> allowedUserIds;
    private boolean killSwitchTriggered;

    public FeatureFlagRule() {
    }

    public FeatureFlagRule(String flagKey, boolean enabled, int rolloutPercentage,
                           Set<String> allowedUserIds, boolean killSwitchTriggered) {
        this.flagKey = flagKey;
        this.enabled = enabled;
        this.rolloutPercentage = rolloutPercentage;
        this.allowedUserIds = allowedUserIds;
        this.killSwitchTriggered = killSwitchTriggered;
    }

    public String getFlagKey() {
        return flagKey;
    }

    public void setFlagKey(String flagKey) {
        this.flagKey = flagKey;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRolloutPercentage() {
        return rolloutPercentage;
    }

    public void setRolloutPercentage(int rolloutPercentage) {
        this.rolloutPercentage = rolloutPercentage;
    }

    public Set<String> getAllowedUserIds() {
        return allowedUserIds;
    }

    public void setAllowedUserIds(Set<String> allowedUserIds) {
        this.allowedUserIds = allowedUserIds;
    }

    public boolean isKillSwitchTriggered() {
        return killSwitchTriggered;
    }

    public void setKillSwitchTriggered(boolean killSwitchTriggered) {
        this.killSwitchTriggered = killSwitchTriggered;
    }
}
