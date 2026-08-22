package com.finflow.troubleshooting.module24;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.TimeZone;

@SpringBootApplication
public class Module24Application {

    private static final Logger log = LoggerFactory.getLogger(Module24Application.class);

    @PostConstruct
    public void init() {
        // Enforce UTC timezone globally across the entire JVM process
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        log.info("[TIMEZONE INITIALIZED] JVM default timezone pinned to UTC (System timezone: {})",
                TimeZone.getDefault().getID());
    }

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        SpringApplication.run(Module24Application.class, args);
    }
}
