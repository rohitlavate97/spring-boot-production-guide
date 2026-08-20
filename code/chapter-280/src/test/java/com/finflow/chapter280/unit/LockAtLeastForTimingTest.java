package com.finflow.chapter280.unit;

import com.finflow.chapter280.Chapter280Application;
import com.finflow.chapter280.correct.MultiPodShedLockSimulator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter280Application.class)
public class LockAtLeastForTimingTest {

    @Autowired
    private MultiPodShedLockSimulator shedLockSimulator;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    public void setup() {
        shedLockSimulator.reset();
        jdbcTemplate.update("DELETE FROM shedlock");
    }

    @Test
    public void testLockAtLeastFor_retainsLockInDbAfterQuickExecution() {
        String taskName = "quickSettlementTask";
        Duration lockAtMostFor = Duration.ofMinutes(5);
        Duration lockAtLeastFor = Duration.ofSeconds(5); // Retain for at least 5s

        // Pod 1 executes quick task (takes < 10ms)
        boolean pod1Executed = shedLockSimulator.executeAsPod("pod-1", taskName, lockAtMostFor, lockAtLeastFor);
        assertThat(pod1Executed).isTrue();
        assertThat(shedLockSimulator.getSuccessCount()).isEqualTo(1);

        // Immediate subsequent attempt by Pod 2 within the 5s window
        boolean pod2Executed = shedLockSimulator.executeAsPod("pod-2", taskName, lockAtMostFor, lockAtLeastFor);

        // Pod 2 is blocked because lockAtLeastFor keeps the lock active in the DB!
        assertThat(pod2Executed).isFalse();
        assertThat(shedLockSimulator.getSuccessCount()).isEqualTo(1);
    }
}
