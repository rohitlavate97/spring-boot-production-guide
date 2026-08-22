package com.finflow.troubleshooting.module28.model;

import java.io.Serializable;

public record IncidentRecord(
        int scenarioId,
        String title,
        String severity,
        String category,
        String symptoms,
        String rootCause,
        String immediateMitigation,
        String permanentRemediation,
        String postMortemDocLink
) implements Serializable {}
