package com.finflow.troubleshooting.module07;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.transaction.annotation.EnableTransactionManagement;

@SpringBootApplication
@EnableTransactionManagement
public class Module07Application {

    public static void main(String[] args) {
        SpringApplication.run(Module07Application.class, args);
    }
}
