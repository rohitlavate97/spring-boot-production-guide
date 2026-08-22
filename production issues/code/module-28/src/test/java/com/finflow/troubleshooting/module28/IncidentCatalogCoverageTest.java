package com.finflow.troubleshooting.module28;

import com.finflow.troubleshooting.module28.model.IncidentRecord;
import com.finflow.troubleshooting.module28.service.IncidentTriageEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IncidentCatalogCoverageTest {

    private IncidentTriageEngine triageEngine;

    @BeforeEach
    void setUp() {
        triageEngine = new IncidentTriageEngine();
    }

    @Test
    @DisplayName("The Incident Catalog MUST contain exactly 20 comprehensive production scenarios with full playbooks")
    void testCatalogCompleteness() {
        List<IncidentRecord> scenarios = triageEngine.getAllScenarios();

        assertThat(scenarios).hasSize(20);

        for (IncidentRecord incident : scenarios) {
            assertThat(incident.scenarioId()).isBetween(1, 20);
            assertThat(incident.title()).isNotBlank();
            assertThat(incident.severity()).isNotBlank();
            assertThat(incident.category()).isNotBlank();
            assertThat(incident.symptoms()).isNotBlank();
            assertThat(incident.rootCause()).isNotBlank();
            assertThat(incident.immediateMitigation()).isNotBlank();
            assertThat(incident.permanentRemediation()).isNotBlank();
            assertThat(incident.postMortemDocLink()).isNotBlank();
        }
    }
}
