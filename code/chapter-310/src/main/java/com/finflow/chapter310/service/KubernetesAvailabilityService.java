package com.finflow.chapter310.service;

import com.finflow.chapter310.domain.PodHealthState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.availability.ApplicationAvailability;
import org.springframework.boot.availability.AvailabilityChangeEvent;
import org.springframework.boot.availability.LivenessState;
import org.springframework.boot.availability.ReadinessState;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class KubernetesAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(KubernetesAvailabilityService.class);

    private final ApplicationAvailability availability;
    private final ApplicationEventPublisher eventPublisher;
    private final AtomicInteger activeConnections = new AtomicInteger(0);

    public KubernetesAvailabilityService(ApplicationAvailability availability,
                                         ApplicationEventPublisher eventPublisher) {
        this.availability = availability;
        this.eventPublisher = eventPublisher;
    }

    public LivenessState getLivenessState() {
        return availability.getLivenessState();
    }

    public ReadinessState getReadinessState() {
        return availability.getReadinessState();
    }

    public void setReadiness(ReadinessState state, String reason) {
        log.warn("Mutating Kubernetes ReadinessState to: {} | Reason: {}", state, reason);
        AvailabilityChangeEvent.publish(eventPublisher, this, state);
    }

    public void setLiveness(LivenessState state, String reason) {
        log.error("Mutating Kubernetes LivenessState to: {} | Reason: {}", state, reason);
        AvailabilityChangeEvent.publish(eventPublisher, this, state);
    }

    public PodHealthState getPodHealthState() {
        long uptimeSeconds = ManagementFactory.getRuntimeMXBean().getUptime() / 1000;
        return new PodHealthState(
                System.getenv().getOrDefault("HOSTNAME", "payment-service-local-pod"),
                getLivenessState().name(),
                getReadinessState().name(),
                activeConnections.get(),
                uptimeSeconds
        );
    }
}
