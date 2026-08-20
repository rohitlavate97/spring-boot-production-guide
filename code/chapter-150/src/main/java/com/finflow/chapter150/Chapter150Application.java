package com.finflow.chapter150;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(
    basePackages = "com.finflow.chapter150",
    excludeFilters = @ComponentScan.Filter(
        type = FilterType.REGEX,
        pattern = "com\\.finflow\\.chapter150\\.incorrect\\..*"
    )
)
public class Chapter150Application {
    public static void main(String[] args) {
        SpringApplication.run(Chapter150Application.class, args);
    }
}
