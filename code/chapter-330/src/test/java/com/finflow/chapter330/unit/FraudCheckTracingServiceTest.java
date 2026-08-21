package com.finflow.chapter330.unit;

import com.finflow.chapter330.Chapter330Application;
import com.finflow.chapter330.service.FraudCheckTracingService;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter330Application.class)
@AutoConfigureObservability
public class FraudCheckTracingServiceTest {

    @Autowired
    private FraudCheckTracingService fraudCheckService;

    @Autowired
    private Tracer tracer;

    @Test
    public void testEvaluateFraudRisk_createsChildSpanAndReturnsDecision() {
        Span parentSpan = tracer.nextSpan().name("test-parent-span").start();

        try (Tracer.SpanInScope ws = tracer.withSpan(parentSpan)) {
            String decision = fraudCheckService.evaluateFraudRisk("MERCH-101", 500.0);
            assertThat(decision).isEqualTo("APPROVED");

            String highRiskDecision = fraudCheckService.evaluateFraudRisk("MERCH-101", 50000.0);
            assertThat(highRiskDecision).isEqualTo("MANUAL_REVIEW");
        } finally {
            parentSpan.end();
        }
    }
}
