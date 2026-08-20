package com.finflow.chapter320.unit;

import com.finflow.chapter320.service.PaymentMetricsService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class HighCardinalityPreventionTest {

    private MeterRegistry registry;
    private PaymentMetricsService metricsService;

    @BeforeEach
    public void setup() {
        registry = new SimpleMeterRegistry();
        metricsService = new PaymentMetricsService(registry);
    }

    @Test
    public void testHighCardinalityAntiPattern_explodesMeterRegistrySize() {
        int transactionCount = 50;

        // Anti-pattern: adds a new meter per unique transaction ID
        for (int i = 0; i < transactionCount; i++) {
            metricsService.recordWithHighCardinality(
                    UUID.randomUUID().toString(),
                    "4111-2222-3333-" + i
            );
        }

        // Verifies that 50 distinct meters were spawned in the registry!
        long spawnedMeters = registry.getMeters().stream()
                .filter(m -> m.getId().getName().equals("payment.unbounded.transactions"))
                .count();

        assertThat(spawnedMeters).isEqualTo(50);
    }
}
