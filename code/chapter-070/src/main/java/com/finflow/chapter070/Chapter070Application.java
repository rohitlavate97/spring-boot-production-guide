package com.finflow.chapter070;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.finflow\\.chapter070\\.incorrect\\..*"
))
public class Chapter070Application {
    public static void main(String[] args) {
        SpringApplication.run(Chapter070Application.class, args);
    }
}
