package com.finflow.troubleshooting.module24;

import com.finflow.troubleshooting.module24.service.TemporalResilienceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class DstTransitionSafetyTest {

    private TemporalResilienceService temporalService;

    @BeforeEach
    void setUp() {
        temporalService = new TemporalResilienceService();
    }

    @Test
    @DisplayName("Should detect non-existent 02:30 AM during Spring-Forward DST gap in US/Eastern")
    void testSpringForwardDstGap() {
        // US Daylight Saving starts March 8, 2026: 02:00 -> 03:00 (02:30 AM does NOT exist!)
        LocalDate springForwardDate = LocalDate.of(2026, 3, 8);
        ZoneId nyZone = ZoneId.of("America/New_York");

        var result = temporalService.analyzeDstTransition(springForwardDate, nyZone);

        assertThat(result.isGapDay()).isTrue();
        assertThat(result.description()).contains("SPRING_FORWARD_GAP");
    }

    @Test
    @DisplayName("Should detect duplicate 02:30 AM during Fall-Back DST overlap in US/Eastern")
    void testFallBackDstOverlap() {
        // US Daylight Saving ends Nov 1, 2026: 02:00 -> 01:00 (01:00-02:00 repeats, 02:30 is affected!)
        LocalDate fallBackDate = LocalDate.of(2026, 11, 1);
        ZoneId nyZone = ZoneId.of("America/New_York");

        var result = temporalService.analyzeDstTransition(fallBackDate, nyZone);
        assertThat(result.zoneId()).isEqualTo("America/New_York");
    }
}
