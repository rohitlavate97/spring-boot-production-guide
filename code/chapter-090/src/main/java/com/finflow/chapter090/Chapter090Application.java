package com.finflow.chapter090;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
        basePackages = "com.finflow.chapter090",
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.finflow\\.chapter090\\.incorrect\\..*")
)
public class Chapter090Application {
    public static void main(String[] args) {
        SpringApplication.run(Chapter090Application.class, args);
    }
}
