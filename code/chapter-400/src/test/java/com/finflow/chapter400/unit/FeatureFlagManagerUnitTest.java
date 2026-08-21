package com.finflow.chapter400.unit;

import com.finflow.chapter400.service.FeatureFlagManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class FeatureFlagManagerUnitTest {

    private FeatureFlagManager featureFlagManager;

    @BeforeEach
    void setUp() {
        featureFlagManager = new FeatureFlagManager();
    }

    @Test
    void testUserWhitelistTargeting() {
        // user_vip_1 is in allowedUserIds for v3_smart_routing_engine
        boolean enabledVip = featureFlagManager.isFeatureEnabled("v3_smart_routing_engine", "user_vip_1");
        assertThat(enabledVip).isTrue();
    }

    @Test
    void testEmergencyKillSwitchOverridesEverything() {
        // Trigger emergency kill switch
        featureFlagManager.triggerEmergencyKillSwitch("v3_smart_routing_engine");

        // Even VIP user must receive FALSE
        boolean enabledVip = featureFlagManager.isFeatureEnabled("v3_smart_routing_engine", "user_vip_1");
        assertThat(enabledVip).isFalse();
    }

    @Test
    void testUpdateRolloutPercentageTo100Percent() {
        featureFlagManager.updateRolloutPercentage("instant_settlement_payouts", 100);

        boolean enabledRandomUser = featureFlagManager.isFeatureEnabled("instant_settlement_payouts", "random_user_99");
        assertThat(enabledRandomUser).isTrue();
    }

    @Test
    void testUnknownFlagReturnsFalse() {
        boolean enabled = featureFlagManager.isFeatureEnabled("unknown_nonexistent_flag", "user_1");
        assertThat(enabled).isFalse();
    }
}
