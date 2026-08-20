package com.finflow.chapter180.correct;

import com.finflow.chapter180.domain.TransferTask;
import com.finflow.chapter180.repository.TransferTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * High-Throughput Queue Consumer using SELECT FOR UPDATE SKIP LOCKED.
 * Allows multiple concurrent worker threads or pods to fetch and lock
 * separate pending tasks without blocking or waiting on each other.
 */
@Service
public class TaskQueueWorkerService {

    private final TransferTaskRepository taskRepository;

    public TaskQueueWorkerService(TransferTaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Transactional
    public List<TransferTask> pollAndLockPendingTasks(int limit) {
        List<TransferTask> tasks = taskRepository.fetchPendingTasksSkipLocked(limit);
        for (TransferTask task : tasks) {
            task.setStatus("PROCESSING");
            taskRepository.save(task);
        }
        return tasks;
    }
}
