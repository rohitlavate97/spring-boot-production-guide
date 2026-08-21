package com.finflow.chapter370;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Chapter370Application {

    public static void main(String[] args) {
        SpringApplication.run(Chapter370Application.class, args);
    }
}
