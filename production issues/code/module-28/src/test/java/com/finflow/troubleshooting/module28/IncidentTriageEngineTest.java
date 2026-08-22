package com.finflow.troubleshooting.module28;

import com.finflow.troubleshooting.module28.service.IncidentTriageEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentTriageEngineTest {

    private IncidentTriageEngine triageEngine;

    @BeforeEach
    void setUp() {
        triageEngine = new IncidentTriageEngine();
    }

    @Test
    @DisplayName("Revenue impacting or high error rate (>5%) MUST be triaged as SEV1_CRITICAL (15m SLA)")
    void testSev1Triage() {
        var decision = triageEngine.triageAlert(7.5, 3000.0, true);

        assertThat(decision.incidentSeverity()).isEqualTo("SEV1_CRITICAL");
        assertThat(decision.targetSlaMinutes()).isEqualTo(15);
        assertThat(decision.incidentCommanderActionPlan()).contains("IMMEDIATE ESCALATION");
    }

    @Test
    @DisplayName("Moderate error rate (2%) with zero revenue impact MUST be triaged as SEV2_MAJOR (45m SLA)")
    void testSev2Triage() {
        var decision = triageEngine.triageAlert(2.0, 400.0, false);

        assertThat(decision.incidentSeverity()).isEqualTo("SEV2_MAJOR");
        assertThat(decision.targetSlaMinutes()).isEqualTo(45);
        assertThat(decision.incidentCommanderActionPlan()).contains("HIGH PRIORITY");
    }

    @Test
    @DisplayName("Low error rate (<1%) MUST be triaged as SEV3_MINOR (240m SLA)")
    void testSev3Triage() {
        var decision = triageEngine.triageAlert(0.2, 100.0, false);

        assertThat(decision.incidentSeverity()).isEqualTo("SEV3_MINOR");
        assertThat(decision.targetSlaMinutes()).isEqualTo(240);
        assertThat(decision.incidentCommanderActionPlan()).contains("STANDARD");
    }
}
