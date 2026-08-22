package com.finflow.troubleshooting.module26;

import com.finflow.troubleshooting.module26.service.CanaryTrafficRouterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CanaryAffinityRoutingTest {

    private CanaryTrafficRouterService routerService;

    @BeforeEach
    void setUp() {
        routerService = new CanaryTrafficRouterService("v1.0.0", "v2.0.0", 10, 1.0, 250);
        routerService.clear();
    }

    @Test
    @DisplayName("A specific User ID MUST deterministically route to the EXACT same version on every request")
    void testUserDeterministicAffinity() {
        String userId = "USR-FINFLOW-4421";

        var first = routerService.routeRequest(userId, null);
        for (int i = 0; i < 50; i++) {
            var subsequent = routerService.routeRequest(userId, null);
            assertThat(subsequent.selectedVersion()).isEqualTo(first.selectedVersion());
            assertThat(subsequent.assignedAffinityCookie()).isEqualTo(first.assignedAffinityCookie());
        }
    }

    @Test
    @DisplayName("Cookie affinity MUST strictly override and lock the user to the canary or baseline version")
    void testCookieAffinityOverride() {
        var canaryDecision = routerService.routeRequest("USR-ANY", "canary");
        assertThat(canaryDecision.selectedVersion()).isEqualTo("v2.0.0");
        assertThat(canaryDecision.routingStrategy()).isEqualTo("COOKIE_AFFINITY_CANARY");

        var baselineDecision = routerService.routeRequest("USR-ANY", "baseline");
        assertThat(baselineDecision.selectedVersion()).isEqualTo("v1.0.0");
        assertThat(baselineDecision.routingStrategy()).isEqualTo("COOKIE_AFFINITY_BASELINE");
    }
}
