package com.finflow.troubleshooting.module22.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

@Configuration
public class SchedulerConfig {

    private static final Logger log = LoggerFactory.getLogger(SchedulerConfig.class);

    /**
     * Production Multi-Threaded TaskScheduler:
     * Eliminates single-threaded starvation where one slow job blocks all other @Scheduled tasks.
     */
    @Bean
    public TaskScheduler taskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(8);
        scheduler.setThreadNamePrefix("finflow-scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(20);
        scheduler.setErrorHandler(throwable ->
                log.error("[SCHEDULER UNCAUGHT EXCEPTION] Scheduled task failed with error: {}", throwable.getMessage(), throwable));
        scheduler.initialize();
        return scheduler;
    }
}
