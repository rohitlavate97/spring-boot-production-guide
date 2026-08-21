package com.finflow.troubleshooting.module12;

import com.finflow.troubleshooting.module12.dto.CreditAssessmentResult;
import com.finflow.troubleshooting.module12.service.CreditAssessmentService;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Module12Application.class)
public class CircuitBreakerStateTransitionTest {

    @Autowired
    private CreditAssessmentService assessmentService;

    @Autowired
    private CircuitBreakerRegistry circuitBreakerRegistry;

    private CircuitBreaker circuitBreaker;

    @BeforeEach
    void setUp() {
        circuitBreaker = circuitBreakerRegistry.circuitBreaker("creditAssessmentService");
        circuitBreaker.reset();
    }

    @Test
    void testCircuitBreakerTransitionsFromClosedToOpenOnRepeatedFailuresAndExecutesFallback() {
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);

        // Trigger failures to breach the 50% threshold on minimum 3 calls
        for (int i = 1; i <= 5; i++) {
            CreditAssessmentResult result = assessmentService.evaluateCredit("CUST-" + i, true, false);
            assertThat(result.isFallbackUsed()).isTrue();
            assertThat(result.getRiskCategory()).isEqualTo("MANUAL_REVIEW_FALLBACK");
        }

        // Circuit should now be in OPEN state
        assertThat(circuitBreaker.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Immediate subsequent calls should fast-fail and return fallback directly
        CreditAssessmentResult fastFailResult = assessmentService.evaluateCredit("CUST-FASTFAIL", false, false);
        assertThat(fastFailResult.isFallbackUsed()).isTrue();
    }
}
