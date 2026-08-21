package com.finflow.chapter360;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Chapter360Application {

    public static void main(String[] args) {
        SpringApplication.run(Chapter360Application.class, args);
    }
}
