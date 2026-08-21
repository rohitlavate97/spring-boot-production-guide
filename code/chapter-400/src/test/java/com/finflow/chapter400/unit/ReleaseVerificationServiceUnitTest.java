package com.finflow.chapter400.unit;

import com.finflow.chapter400.model.CanaryVerificationResult;
import com.finflow.chapter400.service.ReleaseVerificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class ReleaseVerificationServiceUnitTest {

    private ReleaseVerificationService verificationService;

    @BeforeEach
    void setUp() {
        verificationService = new ReleaseVerificationService();
    }

    @Test
    void testCanaryPromotionApprovedWhenMetricsNominal() {
        CanaryVerificationResult result = verificationService.verifyCanaryStep("v3.0.0", 20, 0.1, 45.0);

        assertThat(result.isPromotionApproved()).isTrue();
        assertThat(result.isSmokeTestsPassed()).isTrue();
        assertThat(result.getCurrentTrafficWeightPercent()).isEqualTo(20);
        assertThat(result.getVerificationLogs()).isNotEmpty();
    }

    @Test
    void testCanaryPromotionRejectedWhenErrorRateElevated() {
        // Error rate 1.2% > 0.5% limit
        CanaryVerificationResult result = verificationService.verifyCanaryStep("v3.0.0", 20, 1.2, 45.0);

        assertThat(result.isPromotionApproved()).isFalse();
        assertThat(result.getVerificationLogs().toString()).contains("REJECTED");
    }

    @Test
    void testCanaryPromotionRejectedWhenLatencyElevated() {
        // Latency 220ms > 150ms limit
        CanaryVerificationResult result = verificationService.verifyCanaryStep("v3.0.0", 50, 0.05, 220.0);

        assertThat(result.isPromotionApproved()).isFalse();
        assertThat(result.getVerificationLogs().toString()).contains("REJECTED");
    }
}
