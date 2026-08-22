package com.finflow.troubleshooting.module25;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FlywaySafetyConfigurationTest {

    @Autowired
    private Flyway flyway;

    @Test
    @DisplayName("Flyway configuration MUST enforce cleanDisabled=true to prevent accidental database wipe")
    void testFlywaySafetyConfiguration() {
        assertThat(flyway.getConfiguration().isCleanDisabled()).isTrue();
    }

    @Test
    @DisplayName("All migration scripts (V1, V2) MUST be successfully applied")
    void testMigrationsApplied() {
        MigrationInfo[] applied = flyway.info().applied();
        assertThat(applied).hasSizeGreaterThanOrEqualTo(2);
        assertThat(applied[0].getVersion().getVersion()).isEqualTo("1");
        assertThat(applied[1].getVersion().getVersion()).isEqualTo("2");
    }
}
