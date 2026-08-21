package com.finflow.troubleshooting.module10;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.retry.annotation.EnableRetry;

@SpringBootApplication
@EnableRetry
public class Module10Application {

    public static void main(String[] args) {
        SpringApplication.run(Module10Application.class, args);
    }
}
