package com.finflow.chapter290.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    private static final Logger log = LoggerFactory.getLogger(AsyncConfig.class);

    private final AtomicInteger uncaughtExceptionCount = new AtomicInteger(0);

    /**
     * Production Platform Thread Pool:
     * 1. Bounded queue (10) prevents OutOfMemoryError.
     * 2. CallerRunsPolicy provides natural backpressure when overloaded.
     * 3. Graceful shutdown waits up to 30s for in-flight exports to finish.
     */
    @Bean(name = "statementExportExecutor")
    @org.springframework.context.annotation.Primary
    public Executor statementExportExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(8);
        executor.setQueueCapacity(10);
        executor.setKeepAliveSeconds(60);
        executor.setThreadNamePrefix("stmt-export-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return statementExportExecutor();
    }

    /**
     * Java 21 Virtual Thread TaskExecutor:
     * Lightweight virtual threads for high-concurrency I/O tasks.
     */
    @Bean(name = "virtualThreadExecutor")
    public Executor virtualThreadExecutor() {
        SimpleAsyncTaskExecutor executor = new SimpleAsyncTaskExecutor("virtual-stmt-");
        executor.setVirtualThreads(true); // Enables Java 21 Virtual Threads!
        return executor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return new CustomAsyncExceptionHandler(uncaughtExceptionCount);
    }

    public int getUncaughtExceptionCount() {
        return uncaughtExceptionCount.get();
    }

    public void resetUncaughtExceptionCount() {
        uncaughtExceptionCount.set(0);
    }

    public static class CustomAsyncExceptionHandler implements AsyncUncaughtExceptionHandler {
        private final AtomicInteger counter;

        public CustomAsyncExceptionHandler(AtomicInteger counter) {
            this.counter = counter;
        }

        @Override
        public void handleUncaughtException(Throwable ex, Method method, Object... params) {
            counter.incrementAndGet();
            log.error("Async error in method: {} with message: {}", method.getName(), ex.getMessage(), ex);
        }
    }
}
