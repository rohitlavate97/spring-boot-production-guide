package com.finflow.chapter060;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
    basePackages = "com.finflow.chapter060",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.finflow\\.chapter060\\.incorrect\\..*"
    )
)
public class Chapter060Application {
    public static void main(String[] args) {
        SpringApplication.run(Chapter060Application.class, args);
    }
}
