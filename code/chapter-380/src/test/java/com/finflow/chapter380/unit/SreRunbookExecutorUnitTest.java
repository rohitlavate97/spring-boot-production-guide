package com.finflow.chapter380.unit;

import com.finflow.chapter380.model.DiagnosticSnapshot;
import com.finflow.chapter380.model.IncidentReport;
import com.finflow.chapter380.service.SreRunbookExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SreRunbookExecutorUnitTest {

    private SreRunbookExecutor runbookExecutor;

    @BeforeEach
    void setUp() {
        runbookExecutor = new SreRunbookExecutor();
    }

    @Test
    void testSev1ClassificationWhenCriticalThresholdsBreached() {
        IncidentReport report = runbookExecutor.executeTriageRunbook("INC-9901", 7.2, 2400.0);

        assertThat(report.getIncidentId()).isEqualTo("INC-9901");
        assertThat(report.getSeverity()).isEqualTo("SEV_1");
        assertThat(report.getTitle()).contains("CRITICAL");
        assertThat(report.getStatus()).isEqualTo("INVESTIGATING");
        assertThat(report.getMitigationActions()).hasSizeGreaterThanOrEqualTo(4);
        assertThat(report.getMitigationActions().get(0)).contains("Page On-Call");
    }

    @Test
    void testSev2ClassificationWhenMinorThresholdsBreached() {
        IncidentReport report = runbookExecutor.executeTriageRunbook("INC-9902", 1.5, 300.0);

        assertThat(report.getSeverity()).isEqualTo("SEV_2");
        assertThat(report.getTitle()).contains("MAJOR");
        assertThat(report.getMitigationActions().get(0)).contains("Notify SRE on-call");
    }

    @Test
    void testNominalClassificationWhenOperatingWithinSlo() {
        IncidentReport report = runbookExecutor.executeTriageRunbook("INC-9903", 0.02, 35.0);

        assertThat(report.getSeverity()).isEqualTo("NOMINAL");
        assertThat(report.getTitle()).contains("HEALTHY");
    }

    @Test
    void testCaptureDiagnosticSnapshotPopulatesMemoryAndThreads() {
        DiagnosticSnapshot snapshot = runbookExecutor.captureDiagnosticSnapshot(4.0, 600.0);

        assertThat(snapshot.getActiveThreadCount()).isGreaterThan(0);
        assertThat(snapshot.getTotalMemoryMb()).isGreaterThan(0);
        assertThat(snapshot.getActiveAlerts()).containsKey("HighErrorRateAlert");
        assertThat(snapshot.getActiveAlerts()).containsKey("HighP99LatencyAlert");
    }
}
