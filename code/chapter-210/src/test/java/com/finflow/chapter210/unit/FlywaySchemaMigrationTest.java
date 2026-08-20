package com.finflow.chapter210.unit;

import com.finflow.chapter210.Chapter210Application;
import com.finflow.chapter210.correct.FlywayMigrationInfoService;
import com.finflow.chapter210.dto.MigrationInfoSummary;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter210Application.class)
public class FlywaySchemaMigrationTest {

    @Autowired
    private FlywayMigrationInfoService migrationInfoService;

    @Test
    public void testFlywayMigrations_allAppliedSuccessfully() {
        List<MigrationInfoSummary> history = migrationInfoService.getMigrationHistory();

        // 3 versioned migrations (V1, V2, V3) + 1 repeatable migration (R)
        assertThat(history).hasSize(4);

        assertThat(history).allMatch(info -> info.state().equals("SUCCESS"));

        // Verify version sequence
        assertThat(history.get(0).version()).isEqualTo("1");
        assertThat(history.get(1).version()).isEqualTo("2");
        assertThat(history.get(2).version()).isEqualTo("3");
        assertThat(history.get(3).version()).isEqualTo("REPEATABLE");

        assertThat(migrationInfoService.isSchemaUpToDate()).isTrue();
    }
}
