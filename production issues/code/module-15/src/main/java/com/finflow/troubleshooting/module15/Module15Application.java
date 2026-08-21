package com.finflow.troubleshooting.module15;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class Module15Application {

    public static void main(String[] args) {
        SpringApplication.run(Module15Application.class, args);
    }
}
