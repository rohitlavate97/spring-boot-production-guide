package com.finflow.chapter320.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.actuate.autoconfigure.metrics.MeterRegistryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class MetricsConfig {

    @Bean
    public MeterRegistryCustomizer<MeterRegistry> metricsCommonTags() {
        return registry -> registry.config()
                .commonTags("application", "payment-service", "region", "us-east-1")
                // Deny high-overhead JVM internal metrics if desired
                .meterFilter(MeterFilter.denyNameStartsWith("jvm.gc.memory.allocated"))
                // Enforce SLO boundaries on payment timers
                .meterFilter(new MeterFilter() {
                    @Override
                    public io.micrometer.core.instrument.distribution.DistributionStatisticConfig configure(
                            io.micrometer.core.instrument.Meter.Id id,
                            io.micrometer.core.instrument.distribution.DistributionStatisticConfig config) {
                        if (id.getName().equals("payment.processing.duration")) {
                            return io.micrometer.core.instrument.distribution.DistributionStatisticConfig.builder()
                                    .percentilesHistogram(true)
                                    .serviceLevelObjectives(
                                            Duration.ofMillis(100).toNanos(),
                                            Duration.ofMillis(300).toNanos(),
                                            Duration.ofMillis(500).toNanos()
                                    )
                                    .build()
                                    .merge(config);
                        }
                        return config;
                    }
                });
    }
}
