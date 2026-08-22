package com.finflow.troubleshooting.module20;

import com.finflow.troubleshooting.module20.service.KafkaDiagnosticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PollIntervalBudgetCalculatorTest {

    private KafkaDiagnosticsService diagnosticsService;

    @BeforeEach
    void setUp() {
        diagnosticsService = new KafkaDiagnosticsService();
    }

    @Test
    @DisplayName("Should detect SAFE_POLL_BUDGET when batch time is well under max.poll.interval.ms")
    void testSafePollBudget() {
        // 50 records * 100ms = 5000ms (5s) << 300,000ms (5 mins)
        var result = diagnosticsService.calculatePollBudget(300000, 100, 50);

        assertThat(result.safetyStatus()).isEqualTo("SAFE_POLL_BUDGET");
        assertThat(result.warnings()).isEmpty();
        assertThat(result.estimatedBatchProcessingTimeMs()).isEqualTo(5000);
    }

    @Test
    @DisplayName("Should detect CRITICAL_REBALANCE_STORM_RISK when batch time exceeds max.poll.interval.ms")
    void testCriticalRebalanceStormRisk() {
        // 500 records * 800ms = 400,000ms (400s) > 300,000ms (300s)
        var result = diagnosticsService.calculatePollBudget(300000, 800, 500);

        assertThat(result.safetyStatus()).isEqualTo("CRITICAL_REBALANCE_STORM_RISK");
        assertThat(result.warnings()).anyMatch(w -> w.contains("EXCEEDS max.poll.interval.ms"));
        assertThat(result.recommendedMaxPollRecords()).isLessThan(300);
    }
}
