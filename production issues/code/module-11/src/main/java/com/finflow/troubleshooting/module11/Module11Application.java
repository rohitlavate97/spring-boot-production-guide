package com.finflow.troubleshooting.module11;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class Module11Application {

    public static void main(String[] args) {
        SpringApplication.run(Module11Application.class, args);
    }
}
