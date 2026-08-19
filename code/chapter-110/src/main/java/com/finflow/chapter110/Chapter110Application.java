package com.finflow.chapter110;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableAspectJAutoProxy(exposeProxy = true)
public class Chapter110Application {

    public static void main(String[] args) {
        SpringApplication.run(Chapter110Application.class, args);
    }
}
