package com.finflow.troubleshooting.module26;

import com.finflow.troubleshooting.module26.service.CanaryTrafficRouterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AutomatedCanaryRollbackTest {

    private CanaryTrafficRouterService routerService;

    @BeforeEach
    void setUp() {
        // Max error delta: 1.0%, Max P99: 250ms
        routerService = new CanaryTrafficRouterService("v1.0.0", "v2.0.0", 10, 1.0, 250);
        routerService.clear();
    }

    @Test
    @DisplayName("Healthy canary traffic with low error rate MUST approve progressive rollout")
    void testHealthyCanaryApproval() {
        // 90 baseline requests (0 errors)
        for (int i = 0; i < 90; i++) {
            routerService.recordTraffic(false, false, 20);
        }

        // 15 canary requests (0 errors, 25ms latency)
        for (int i = 0; i < 15; i++) {
            routerService.recordTraffic(true, false, 25);
        }

        var report = routerService.evaluateCanaryHealth();
        assertThat(report.decision()).isEqualTo("PROCEED_WITH_PROGRESSIVE_ROLLOUT");
        assertThat(report.deltaErrorRatePercent()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Elevated error rate on Canary MUST trigger AUTOMATED_ROLLBACK_TRIGGERED")
    void testCanaryErrorRateSpikeTriggersRollback() {
        // 100 baseline requests (0 errors)
        for (int i = 0; i < 100; i++) {
            routerService.recordTraffic(false, false, 20);
        }

        // 20 canary requests (3 errors = 15% error rate >> 1.0% allowed delta)
        for (int i = 0; i < 17; i++) {
            routerService.recordTraffic(true, false, 25);
        }
        for (int i = 0; i < 3; i++) {
            routerService.recordTraffic(true, true, 25);
        }

        var report = routerService.evaluateCanaryHealth();
        assertThat(report.decision()).isEqualTo("AUTOMATED_ROLLBACK_TRIGGERED");
        assertThat(report.rationale()).anyMatch(r -> r.contains("exceeds baseline"));
    }

    @Test
    @DisplayName("High P99 latency on Canary MUST trigger AUTOMATED_ROLLBACK_TRIGGERED")
    void testCanaryHighLatencyTriggersRollback() {
        // 50 baseline requests
        for (int i = 0; i < 50; i++) {
            routerService.recordTraffic(false, false, 20);
        }

        // 20 canary requests with P99 latency = 500ms > 250ms max
        for (int i = 0; i < 20; i++) {
            routerService.recordTraffic(true, false, 500);
        }

        var report = routerService.evaluateCanaryHealth();
        assertThat(report.decision()).isEqualTo("AUTOMATED_ROLLBACK_TRIGGERED");
        assertThat(report.rationale()).anyMatch(r -> r.contains("exceeds maximum threshold"));
    }
}
