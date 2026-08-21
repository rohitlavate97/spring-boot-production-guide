package com.finflow.troubleshooting.module02;

import com.finflow.troubleshooting.module02.config.FinFlowCoreProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FinFlowCoreProperties.class)
public class Module02Application {

    public static void main(String[] args) {
        SpringApplication.run(Module02Application.class, args);
    }
}
