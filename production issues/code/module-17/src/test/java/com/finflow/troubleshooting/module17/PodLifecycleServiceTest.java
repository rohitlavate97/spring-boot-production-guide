package com.finflow.troubleshooting.module17;

import com.finflow.troubleshooting.module17.service.PodLifecycleService;
import com.finflow.troubleshooting.module17.service.SimulatedDownstreamDependencyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PodLifecycleServiceTest {

    @Autowired
    private PodLifecycleService lifecycleService;

    @Autowired
    private ApplicationAvailability availability;

    @Autowired
    private SimulatedDownstreamDependencyService downstreamService;

    @Test
    @DisplayName("Should initialize with CORRECT liveness and ACCEPTING_TRAFFIC readiness")
    void testInitialAvailabilityState() {
        assertThat(availability.getLivenessState()).isEqualTo(LivenessState.CORRECT);
        assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.ACCEPTING_TRAFFIC);

        Map<String, Object> status = lifecycleService.getLifecycleStatus();
        assertThat(status.get("livenessState")).isEqualTo("CORRECT");
        assertThat(status.get("readinessState")).isEqualTo("ACCEPTING_TRAFFIC");
    }

    @Test
    @DisplayName("Should successfully transition readiness to REFUSING_TRAFFIC and back to ACCEPTING_TRAFFIC")
    void testReadinessStateTransitions() {
        lifecycleService.drainTraffic();
        assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.REFUSING_TRAFFIC);

        lifecycleService.acceptTraffic();
        assertThat(availability.getReadinessState()).isEqualTo(ReadinessState.ACCEPTING_TRAFFIC);
    }

    @Test
    @DisplayName("Should track in-flight request registration count accurately")
    void testInflightRequestTracking() {
        long current = lifecycleService.registerInflightStart();
        assertThat(current).isGreaterThanOrEqualTo(1);

        long afterEnd = lifecycleService.registerInflightEnd();
        assertThat(afterEnd).isEqualTo(current - 1);
    }
}
