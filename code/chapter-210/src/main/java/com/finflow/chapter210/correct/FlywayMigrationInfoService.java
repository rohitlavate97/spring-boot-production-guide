package com.finflow.chapter210.correct;

import com.finflow.chapter210.dto.MigrationInfoSummary;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class FlywayMigrationInfoService {

    private final Flyway flyway;

    public FlywayMigrationInfoService(Flyway flyway) {
        this.flyway = flyway;
    }

    /**
     * Inspects the current state of applied Flyway migrations from flyway_schema_history.
     */
    public List<MigrationInfoSummary> getMigrationHistory() {
        MigrationInfo[] all = flyway.info().all();
        return Arrays.stream(all)
                .map(info -> new MigrationInfoSummary(
                        info.getVersion() != null ? info.getVersion().getVersion() : "REPEATABLE",
                        info.getDescription(),
                        info.getType().name(),
                        info.getScript(),
                        info.getState().name(),
                        info.getChecksum()
                ))
                .toList();
    }

    public boolean isSchemaUpToDate() {
        MigrationInfo current = flyway.info().current();
        return current != null && current.getState().isApplied();
    }
}
