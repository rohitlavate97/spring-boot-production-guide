package com.finflow.chapter400.service;

import com.finflow.chapter400.model.FeatureFlagRule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance, in-memory Feature Flag Manager supporting percentage rollouts,
 * user whitelist targeting, and sub-millisecond emergency kill switches.
 */
@Service
public class FeatureFlagManager {

    private static final Logger log = LoggerFactory.getLogger(FeatureFlagManager.class);

    private final Map<String, FeatureFlagRule> flagRegistry = new ConcurrentHashMap<>();

    public FeatureFlagManager() {
        // Initialize default enterprise feature flags
        flagRegistry.put("v3_smart_routing_engine", new FeatureFlagRule(
                "v3_smart_routing_engine", true, 20, Set.of("user_vip_1", "user_beta_tester"), false));

        flagRegistry.put("instant_settlement_payouts", new FeatureFlagRule(
                "instant_settlement_payouts", false, 0, Set.of("merchant_alpha"), false));

        flagRegistry.put("dual_write_legacy_adapter", new FeatureFlagRule(
                "dual_write_legacy_adapter", true, 100, Set.of(), false));
    }

    public boolean isFeatureEnabled(String flagKey, String userId) {
        FeatureFlagRule rule = flagRegistry.get(flagKey);
        if (rule == null) {
            return false;
        }

        // 1. Emergency Kill Switch check (Overrides everything!)
        if (rule.isKillSwitchTriggered()) {
            log.warn("[FeatureFlag] Kill-switch ACTIVE for flag '{}'. Evaluation forced to FALSE.", flagKey);
            return false;
        }

        // 2. Global Enablement check
        if (!rule.isEnabled()) {
            return false;
        }

        // 3. User Whitelist targeting
        if (userId != null && rule.getAllowedUserIds() != null && rule.getAllowedUserIds().contains(userId)) {
            return true;
        }

        // 4. Percentage Rollout (Deterministic MD5 Hash distribution)
        if (rule.getRolloutPercentage() <= 0) {
            return false;
        }
        if (rule.getRolloutPercentage() >= 100) {
            return true;
        }

        return evaluateDeterministicRollout(flagKey, userId, rule.getRolloutPercentage());
    }

    public void triggerEmergencyKillSwitch(String flagKey) {
        FeatureFlagRule rule = flagRegistry.get(flagKey);
        if (rule != null) {
            rule.setKillSwitchTriggered(true);
            log.error("[FeatureFlag] EMERGENCY KILL-SWITCH TRIGGERED for flag '{}'!", flagKey);
        }
    }

    public void updateRolloutPercentage(String flagKey, int newPercentage) {
        FeatureFlagRule rule = flagRegistry.get(flagKey);
        if (rule != null) {
            int pct = Math.max(0, Math.min(100, newPercentage));
            rule.setRolloutPercentage(pct);
            if (pct > 0) {
                rule.setEnabled(true);
            }
            rule.setKillSwitchTriggered(false);
            log.info("[FeatureFlag] Updated flag '{}' rollout to {}%", flagKey, rule.getRolloutPercentage());
        }
    }

    public void setFlagRule(FeatureFlagRule rule) {
        flagRegistry.put(rule.getFlagKey(), rule);
    }

    public Map<String, Boolean> getAllFlagStates() {
        Map<String, Boolean> states = new ConcurrentHashMap<>();
        flagRegistry.forEach((key, rule) -> states.put(key, rule.isEnabled() && !rule.isKillSwitchTriggered()));
        return states;
    }

    private boolean evaluateDeterministicRollout(String flagKey, String userId, int targetPercentage) {
        String target = (userId != null && !userId.isBlank()) ? userId : "anonymous_guest";
        String seed = flagKey + ":" + target;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hash = md.digest(seed.getBytes(StandardCharsets.UTF_8));
            int bucket = ((hash[0] & 0xFF) << 8 | (hash[1] & 0xFF)) % 100;
            return bucket < targetPercentage;
        } catch (NoSuchAlgorithmException e) {
            return Math.abs(seed.hashCode()) % 100 < targetPercentage;
        }
    }
}
