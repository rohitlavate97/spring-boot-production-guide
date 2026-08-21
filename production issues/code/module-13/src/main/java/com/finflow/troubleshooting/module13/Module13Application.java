package com.finflow.troubleshooting.module13;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class Module13Application {

    public static void main(String[] args) {
        SpringApplication.run(Module13Application.class, args);
    }
}
