package com.finflow.chapter130;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
        basePackages = "com.finflow.chapter130",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.finflow\\.chapter130\\.incorrect\\..*"
        )
)
public class Chapter130Application {
    public static void main(String[] args) {
        SpringApplication.run(Chapter130Application.class, args);
    }
}
