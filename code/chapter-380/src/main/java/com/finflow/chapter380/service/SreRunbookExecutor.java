package com.finflow.chapter380.service;

import com.finflow.chapter380.model.DiagnosticSnapshot;
import com.finflow.chapter380.model.IncidentReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.*;

/**
 * Automated SRE Runbook Engine executing automated incident triage,
 * SLO burn-rate evaluation, and diagnostic snapshot generation.
 */
@Service
public class SreRunbookExecutor {

    private static final Logger log = LoggerFactory.getLogger(SreRunbookExecutor.class);

    private static final double SLO_ERROR_RATE_SEV1_THRESHOLD = 5.0; // > 5% errors is SEV-1
    private static final double SLO_ERROR_RATE_SEV2_THRESHOLD = 1.0; // > 1% errors is SEV-2
    private static final double SLO_P99_LATENCY_SEV1_THRESHOLD_MS = 2000.0; // > 2s is SEV-1
    private static final double SLO_P99_LATENCY_SEV2_THRESHOLD_MS = 500.0;  // > 500ms is SEV-2

    public DiagnosticSnapshot captureDiagnosticSnapshot(double simulatedErrorRate, double simulatedP99Latency) {
        ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        int activeThreads = threadMXBean.getThreadCount();

        Runtime runtime = Runtime.getRuntime();
        long freeMb = runtime.freeMemory() / (1024 * 1024);
        long totalMb = runtime.totalMemory() / (1024 * 1024);
        long maxMb = runtime.maxMemory() / (1024 * 1024);

        Map<String, String> activeAlerts = new HashMap<>();
        if (simulatedErrorRate > SLO_ERROR_RATE_SEV2_THRESHOLD) {
            activeAlerts.put("HighErrorRateAlert", "Error rate at " + simulatedErrorRate + "% exceeds SLO threshold");
        }
        if (simulatedP99Latency > SLO_P99_LATENCY_SEV2_THRESHOLD_MS) {
            activeAlerts.put("HighP99LatencyAlert", "P99 latency at " + simulatedP99Latency + "ms exceeds SLO threshold");
        }

        return new DiagnosticSnapshot(
                activeThreads, freeMb, totalMb, maxMb,
                simulatedErrorRate, simulatedP99Latency, activeAlerts
        );
    }

    public IncidentReport executeTriageRunbook(String incidentId, double observedErrorRate, double observedP99Latency) {
        log.warn("[SreRunbook] Initiating automated incident triage for incident '{}' | ErrorRate: {}% | P99: {}ms",
                incidentId, observedErrorRate, observedP99Latency);

        String severity;
        String title;
        String triggeredSignal;
        List<String> mitigations = new ArrayList<>();

        if (observedErrorRate >= SLO_ERROR_RATE_SEV1_THRESHOLD || observedP99Latency >= SLO_P99_LATENCY_SEV1_THRESHOLD_MS) {
            severity = "SEV_1";
            title = "CRITICAL: Multiple Golden Signals Breached — Rapid Error Budget Depletion";
            triggeredSignal = "ErrorRate=" + observedErrorRate + "% (Limit: 5%), P99Latency=" + observedP99Latency + "ms (Limit: 2000ms)";
            mitigations.add("1. Page On-Call Incident Commander and Payment Domain Lead via PagerDuty");
            mitigations.add("2. Trigger Circuit Breaker fallback for degraded downstream dependencies");
            mitigations.add("3. Auto-scale Kubernetes Deployment HPA from 20 -> 40 pods");
            mitigations.add("4. Capture Heap Dump & Thread Dump via /actuator/threaddump for root-cause analysis");
            mitigations.add("5. Route traffic to secondary multi-region standby cluster if error rate persists > 5m");
        } else if (observedErrorRate >= SLO_ERROR_RATE_SEV2_THRESHOLD || observedP99Latency >= SLO_P99_LATENCY_SEV2_THRESHOLD_MS) {
            severity = "SEV_2";
            title = "MAJOR: Elevated Latency or Minor Error Budget Burn";
            triggeredSignal = "ErrorRate=" + observedErrorRate + "% (Limit: 1%), P99Latency=" + observedP99Latency + "ms (Limit: 500ms)";
            mitigations.add("1. Notify SRE on-call channel via Slack webhook");
            mitigations.add("2. Enable DEBUG logging on payment gateway client via /actuator/loggers");
            mitigations.add("3. Monitor database connection pool saturation on HikariCP metrics");
        } else {
            severity = "NOMINAL";
            title = "HEALTHY: All Golden Signals within SLO specifications";
            triggeredSignal = "None";
            mitigations.add("No remediation required. System operating normally.");
        }

        IncidentReport report = new IncidentReport(
                incidentId, severity, title, triggeredSignal,
                observedErrorRate, observedP99Latency, mitigations, "INVESTIGATING"
        );

        log.info("[SreRunbook] Triage complete for '{}'. Severity: {} | Mitigations generated: {}",
                incidentId, severity, mitigations.size());

        return report;
    }
}
