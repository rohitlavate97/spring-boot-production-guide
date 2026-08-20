package com.finflow.chapter180.unit;

import com.finflow.chapter180.Chapter180Application;
import com.finflow.chapter180.correct.TaskQueueWorkerService;
import com.finflow.chapter180.domain.TransferTask;
import com.finflow.chapter180.repository.TransferTaskRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = Chapter180Application.class)
public class SkipLockedQueueTest {

    @Autowired
    private TransferTaskRepository taskRepository;

    @Autowired
    private TaskQueueWorkerService workerService;

    @BeforeEach
    public void setup() {
        taskRepository.deleteAll();

        for (int i = 1; i <= 10; i++) {
            taskRepository.save(new TransferTask(
                    UUID.randomUUID(),
                    "MERCH_SRC_" + i,
                    "MERCH_DST_" + i,
                    BigDecimal.valueOf(100.00 * i),
                    "PENDING",
                    Instant.now().plusMillis(i * 10)
            ));
        }
        taskRepository.flush();
    }

    @Test
    public void testSkipLocked_pollsAndLocksBatchOfTasks() {
        List<TransferTask> batch = workerService.pollAndLockPendingTasks(5);

        assertThat(batch).hasSize(5);
        assertThat(batch).allMatch(t -> t.getStatus().equals("PROCESSING"));

        List<TransferTask> remainingPending = taskRepository.findAllByStatus("PENDING");
        assertThat(remainingPending).hasSize(5);
    }
}
