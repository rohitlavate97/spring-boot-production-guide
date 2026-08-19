package com.finflow.chapter050;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
    basePackages = "com.finflow.chapter050",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.finflow\\.chapter050\\.incorrect\\..*"
    )
)
public class Chapter050Application {
    public static void main(String[] args) {
        SpringApplication.run(Chapter050Application.class, args);
    }
}
