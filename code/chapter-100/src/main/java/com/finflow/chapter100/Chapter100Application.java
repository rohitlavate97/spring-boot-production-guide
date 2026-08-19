package com.finflow.chapter100;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(basePackages = "com.finflow.chapter100",
        excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "com\\.finflow\\.chapter100\\.incorrect\\..*"))
public class Chapter100Application {
    public static void main(String[] args) {
        SpringApplication.run(Chapter100Application.class, args);
    }
}
