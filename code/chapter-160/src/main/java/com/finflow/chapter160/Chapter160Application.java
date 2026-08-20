package com.finflow.chapter160;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
    basePackages = "com.finflow.chapter160",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.finflow\\.chapter160\\.incorrect\\..*"
    )
)
public class Chapter160Application {
    public static void main(String[] args) {
        SpringApplication.run(Chapter160Application.class, args);
    }
}
