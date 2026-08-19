package com.finflow.chapter080;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
    basePackages = "com.finflow.chapter080",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.finflow\\.chapter080\\.incorrect\\..*"
    )
)
public class Chapter080Application {
    public static void main(String[] args) {
        SpringApplication.run(Chapter080Application.class, args);
    }
}
