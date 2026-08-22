package com.finflow.troubleshooting.module24;

import com.finflow.troubleshooting.module24.service.TemporalResilienceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class ClockSkewToleranceTest {

    private TemporalResilienceService temporalService;

    @BeforeEach
    void setUp() {
        temporalService = new TemporalResilienceService();
    }

    @Test
    @DisplayName("A token strictly expired by 3s MUST be accepted if clock skew leeway is 5s, but rejected if leeway is 0s")
    void testClockSkewToleranceAbsorption() {
        Instant now = Instant.now();
        Instant tokenExpiry = now.minusSeconds(3); // Expired 3 seconds ago on strict evaluation

        // 1. Without leeway (strict 0s): must fail
        var strictResult = temporalService.validateTokenWithClockSkewLeeway(tokenExpiry, now, 0);
        assertThat(strictResult.isValidWithoutLeeway()).isFalse();
        assertThat(strictResult.isValidWithLeeway()).isFalse();
        assertThat(strictResult.message()).isEqualTo("TOKEN_EXPIRED");

        // 2. With 5s clock skew leeway: must pass!
        var leewayResult = temporalService.validateTokenWithClockSkewLeeway(tokenExpiry, now, 5);
        assertThat(leewayResult.isValidWithoutLeeway()).isFalse();
        assertThat(leewayResult.isValidWithLeeway()).isTrue();
        assertThat(leewayResult.message()).isEqualTo("TOKEN_VALID_WITH_CLOCK_SKEW_LEEWAY");
    }
}
