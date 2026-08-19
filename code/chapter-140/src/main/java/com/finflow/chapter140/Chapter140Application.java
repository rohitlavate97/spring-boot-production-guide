package com.finflow.chapter140;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
    basePackages = "com.finflow.chapter140",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.finflow\\.chapter140\\.incorrect\\..*"
    )
)
public class Chapter140Application {
    public static void main(String[] args) {
        SpringApplication.run(Chapter140Application.class, args);
    }
}
