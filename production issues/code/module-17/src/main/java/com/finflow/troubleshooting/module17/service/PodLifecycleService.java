package com.finflow.troubleshooting.module17.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PodLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(PodLifecycleService.class);

    private final ApplicationAvailability availability;
    private final ApplicationEventPublisher eventPublisher;
    private final SimulatedDownstreamDependencyService downstreamService;
    private final AtomicLong inflightRequests = new AtomicLong(0);

    public PodLifecycleService(ApplicationAvailability availability,
                               ApplicationEventPublisher eventPublisher,
                               SimulatedDownstreamDependencyService downstreamService) {
        this.availability = availability;
        this.eventPublisher = eventPublisher;
        this.downstreamService = downstreamService;
    }

    public Map<String, Object> getLifecycleStatus() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("livenessState", availability.getLivenessState().name());
        status.put("readinessState", availability.getReadinessState().name());
        status.put("downstreamDbReachable", downstreamService.isDatabaseReachable());
        status.put("downstreamRedisReachable", downstreamService.isRedisReachable());
        status.put("inflightRequests", inflightRequests.get());
        return status;
    }

    public void drainTraffic() {
        log.warn("Initiating graceful pod traffic drain: Publishing ReadinessState.REFUSING_TRAFFIC");
        AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.REFUSING_TRAFFIC);
    }

    public void acceptTraffic() {
        log.info("Restoring pod traffic routing: Publishing ReadinessState.ACCEPTING_TRAFFIC");
        AvailabilityChangeEvent.publish(eventPublisher, this, ReadinessState.ACCEPTING_TRAFFIC);
    }

    public void breakLiveness() {
        log.error("Simulating unrecoverable JVM deadlock: Publishing LivenessState.BROKEN");
        AvailabilityChangeEvent.publish(eventPublisher, this, LivenessState.BROKEN);
    }

    public void restoreLiveness() {
        log.info("Restoring JVM liveness state: Publishing LivenessState.CORRECT");
        AvailabilityChangeEvent.publish(eventPublisher, this, LivenessState.CORRECT);
    }

    public long registerInflightStart() {
        return inflightRequests.incrementAndGet();
    }

    public long registerInflightEnd() {
        return inflightRequests.decrementAndGet();
    }
}
