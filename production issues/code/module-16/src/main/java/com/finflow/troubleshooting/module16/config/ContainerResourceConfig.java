package com.finflow.troubleshooting.module16.config;

import com.finflow.troubleshooting.module16.service.CgroupDiagnosticsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class ContainerResourceConfig {

    private static final Logger log = LoggerFactory.getLogger(ContainerResourceConfig.class);

    @Bean(name = "containerAwareExecutor")
    public Executor containerAwareExecutor(CgroupDiagnosticsService cgroupService) {
        int processors = Runtime.getRuntime().availableProcessors();
        String cgroupVer = cgroupService.detectCgroupVersion();
        double quotaCores = cgroupService.readCgroupCpuQuota(cgroupVer);

        // Core pool size: at least 2, or Math.max(2, (int) Math.ceil(quotaCores * 2))
        int effectiveCores = quotaCores > 0 ? (int) Math.ceil(quotaCores) : processors;
        int corePoolSize = Math.max(2, effectiveCores * 2);
        int maxPoolSize = Math.max(4, effectiveCores * 4);

        log.info("Configuring ContainerAwareExecutor: detectedProcessors={}, cgroupCpuQuota={}, corePoolSize={}, maxPoolSize={}",
                processors, quotaCores, corePoolSize, maxPoolSize);

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(corePoolSize);
        executor.setMaxPoolSize(maxPoolSize);
        executor.setQueueCapacity(200);
        executor.setThreadNamePrefix("cgroup-worker-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(20);
        executor.initialize();
        return executor;
    }
}
