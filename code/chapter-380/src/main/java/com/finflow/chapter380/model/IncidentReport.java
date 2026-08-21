package com.finflow.chapter380.model;

import java.time.Instant;
import java.util.List;

public class IncidentReport {

    private String incidentId;
    private String severity; // SEV_1, SEV_2, SEV_3, NOMINAL
    private String title;
    private String triggeredSignal;
    private double currentErrorRatePercent;
    private double currentP99LatencyMs;
    private List<String> mitigationActions;
    private String status; // INVESTIGATING, MITIGATED, RESOLVED
    private Instant createdAt;

    public IncidentReport() {
        this.createdAt = Instant.now();
    }

    public IncidentReport(String incidentId, String severity, String title, String triggeredSignal,
                          double currentErrorRatePercent, double currentP99LatencyMs,
                          List<String> mitigationActions, String status) {
        this.incidentId = incidentId;
        this.severity = severity;
        this.title = title;
        this.triggeredSignal = triggeredSignal;
        this.currentErrorRatePercent = currentErrorRatePercent;
        this.currentP99LatencyMs = currentP99LatencyMs;
        this.mitigationActions = mitigationActions;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public String getIncidentId() {
        return incidentId;
    }

    public void setIncidentId(String incidentId) {
        this.incidentId = incidentId;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTriggeredSignal() {
        return triggeredSignal;
    }

    public void setTriggeredSignal(String triggeredSignal) {
        this.triggeredSignal = triggeredSignal;
    }

    public double getCurrentErrorRatePercent() {
        return currentErrorRatePercent;
    }

    public void setCurrentErrorRatePercent(double currentErrorRatePercent) {
        this.currentErrorRatePercent = currentErrorRatePercent;
    }

    public double getCurrentP99LatencyMs() {
        return currentP99LatencyMs;
    }

    public void setCurrentP99LatencyMs(double currentP99LatencyMs) {
        this.currentP99LatencyMs = currentP99LatencyMs;
    }

    public List<String> getMitigationActions() {
        return mitigationActions;
    }

    public void setMitigationActions(List<String> mitigationActions) {
        this.mitigationActions = mitigationActions;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
