package com.finflow.troubleshooting.module26.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class CanaryTrafficRouterService {

    private static final Logger log = LoggerFactory.getLogger(CanaryTrafficRouterService.class);

    public record RoutingDecision(
            String selectedVersion,
            String routingStrategy,
            String assignedAffinityCookie
    ) {}

    public record CanaryHealthReport(
            long baselineRequests,
            double baselineErrorRatePercent,
            long canaryRequests,
            double canaryErrorRatePercent,
            double deltaErrorRatePercent,
            double canaryP99LatencyMs,
            String decision,
            List<String> rationale
    ) {}

    private final String baselineVersion;
    private final String canaryVersion;
    private final int canaryWeightPercent;
    private final double maxAllowedErrorRatePercent;
    private final double maxAllowedP99LatencyMs;

    private final AtomicLong baselineRequests = new AtomicLong(0);
    private final AtomicLong baselineErrors = new AtomicLong(0);
    private final AtomicLong canaryRequests = new AtomicLong(0);
    private final AtomicLong canaryErrors = new AtomicLong(0);
    private final List<Long> canaryLatencies = Collections.synchronizedList(new ArrayList<>());

    public CanaryTrafficRouterService(
            @Value("${finflow.deployment.baseline-version:v1.0.0}") String baselineVersion,
            @Value("${finflow.deployment.canary-version:v2.0.0}") String canaryVersion,
            @Value("${finflow.deployment.canary-weight-percent:10}") int canaryWeightPercent,
            @Value("${finflow.deployment.max-allowed-error-rate-percent:1.0}") double maxAllowedErrorRatePercent,
            @Value("${finflow.deployment.max-allowed-p99-latency-ms:250}") double maxAllowedP99LatencyMs
    ) {
        this.baselineVersion = baselineVersion;
        this.canaryVersion = canaryVersion;
        this.canaryWeightPercent = canaryWeightPercent;
        this.maxAllowedErrorRatePercent = maxAllowedErrorRatePercent;
        this.maxAllowedP99LatencyMs = maxAllowedP99LatencyMs;
    }

    /**
     * ✅ PRODUCTION FIX 1: Deterministic Canary Sticky Routing
     * Eliminates split-brain where a user's sub-requests bounce between v1 and v2.
     */
    public RoutingDecision routeRequest(String userId, String canaryCookie) {
        if ("canary".equalsIgnoreCase(canaryCookie)) {
            recordTraffic(true, false, 25);
            return new RoutingDecision(canaryVersion, "COOKIE_AFFINITY_CANARY", "canary");
        } else if ("baseline".equalsIgnoreCase(canaryCookie)) {
            recordTraffic(false, false, 20);
            return new RoutingDecision(baselineVersion, "COOKIE_AFFINITY_BASELINE", "baseline");
        }

        // Hash-based deterministic weighting by User ID
        int hashBucket = Math.abs(userId.hashCode()) % 100;
        boolean isCanary = hashBucket < canaryWeightPercent;

        recordTraffic(isCanary, false, isCanary ? 30 : 20);

        String version = isCanary ? canaryVersion : baselineVersion;
        String cookie = isCanary ? "canary" : "baseline";

        return new RoutingDecision(version, "USER_HASH_DETERMINISTIC", cookie);
    }

    public void recordTraffic(boolean isCanary, boolean isError, long latencyMs) {
        if (isCanary) {
            canaryRequests.incrementAndGet();
            if (isError) canaryErrors.incrementAndGet();
            canaryLatencies.add(latencyMs);
        } else {
            baselineRequests.incrementAndGet();
            if (isError) baselineErrors.incrementAndGet();
        }
    }

    /**
     * ✅ PRODUCTION FIX 2: Automated Canary Analysis (ACA) Decision Engine
     */
    public CanaryHealthReport evaluateCanaryHealth() {
        long bReq = baselineRequests.get();
        long bErr = baselineErrors.get();
        long cReq = canaryRequests.get();
        long cErr = canaryErrors.get();

        double bRate = bReq > 0 ? ((double) bErr / bReq) * 100.0 : 0.0;
        double cRate = cReq > 0 ? ((double) cErr / cReq) * 100.0 : 0.0;
        double deltaRate = cRate - bRate;

        double p99Latency = calculateP99Latency();

        List<String> rationale = new ArrayList<>();
        String decision;

        if (deltaRate > maxAllowedErrorRatePercent) {
            decision = "AUTOMATED_ROLLBACK_TRIGGERED";
            rationale.add("Canary error rate (" + String.format("%.2f", cRate) + "%) exceeds baseline ("
                    + String.format("%.2f", bRate) + "%) by >" + maxAllowedErrorRatePercent + "%!");
        } else if (p99Latency > maxAllowedP99LatencyMs) {
            decision = "AUTOMATED_ROLLBACK_TRIGGERED";
            rationale.add("Canary P99 latency (" + p99Latency + "ms) exceeds maximum threshold (" + maxAllowedP99LatencyMs + "ms)!");
        } else if (cReq < 10) {
            decision = "INSUFFICIENT_CANARY_SAMPLE_SIZE";
            rationale.add("Need at least 10 canary samples before making automated promotion decision.");
        } else {
            decision = "PROCEED_WITH_PROGRESSIVE_ROLLOUT";
            rationale.add("Canary metrics healthy! Ready to scale traffic to next stage (25% -> 50% -> 100%).");
        }

        return new CanaryHealthReport(bReq, bRate, cReq, cRate, deltaRate, p99Latency, decision, rationale);
    }

    private double calculateP99Latency() {
        if (canaryLatencies.isEmpty()) return 20.0;
        List<Long> copy;
        synchronized (canaryLatencies) {
            copy = new ArrayList<>(canaryLatencies);
        }
        Collections.sort(copy);
        int index = (int) Math.ceil(0.99 * copy.size()) - 1;
        return copy.get(Math.max(0, index));
    }

    public void clear() {
        baselineRequests.set(0);
        baselineErrors.set(0);
        canaryRequests.set(0);
        canaryErrors.set(0);
        canaryLatencies.clear();
    }
}
