package com.finflow.chapter120;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
        basePackages = "com.finflow.chapter120",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.finflow\\.chapter120\\.incorrect\\..*"
        )
)
public class Chapter120Application {
    public static void main(String[] args) {
        SpringApplication.run(Chapter120Application.class, args);
    }
}
