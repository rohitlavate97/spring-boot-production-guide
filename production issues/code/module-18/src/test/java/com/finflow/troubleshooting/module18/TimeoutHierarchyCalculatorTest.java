package com.finflow.troubleshooting.module18;

import com.finflow.troubleshooting.module18.service.TimeoutHierarchyCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TimeoutHierarchyCalculatorTest {

    private TimeoutHierarchyCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new TimeoutHierarchyCalculator();
    }

    @Test
    @DisplayName("Should validate SAFE_KEEP_ALIVE_HIERARCHY when Tomcat keepalive exceeds Nginx keepalive")
    void testSafeKeepAliveHierarchy() {
        var result = calculator.validateHierarchy(15000, 10000, 9000, 8000, 65, 70);

        assertThat(result.keepAliveSafetyStatus()).isEqualTo("SAFE_KEEP_ALIVE_HIERARCHY");
        assertThat(result.timeoutHierarchyStatus()).isEqualTo("VALID_TIMEOUT_HIERARCHY");
        assertThat(result.violations()).isEmpty();
    }

    @Test
    @DisplayName("Should detect CRITICAL_KEEP_ALIVE_RACE_CONDITION when Tomcat keepalive is <= Nginx keepalive")
    void testKeepAliveRaceConditionDetected() {
        var result = calculator.validateHierarchy(15000, 10000, 9000, 8000, 65, 20);

        assertThat(result.keepAliveSafetyStatus()).isEqualTo("CRITICAL_KEEP_ALIVE_RACE_CONDITION");
        assertThat(result.violations()).anyMatch(v -> v.contains("Tomcat keep-alive timeout (20s) is <= Nginx keepalive_timeout (65s)"));
    }

    @Test
    @DisplayName("Should detect INVALID_TIMEOUT_HIERARCHY when Gateway timeout is less than downstream API timeout")
    void testInvalidTimeoutHierarchyDetected() {
        // Gateway timeout: 5000ms < Downstream API timeout: 8000ms
        var result = calculator.validateHierarchy(15000, 5000, 9000, 8000, 65, 70);

        assertThat(result.timeoutHierarchyStatus()).isEqualTo("INVALID_TIMEOUT_HIERARCHY");
        assertThat(result.violations()).anyMatch(v -> v.contains("Gateway timeout (5000ms) is LESS than downstream API timeout (8000ms)"));
    }
}
