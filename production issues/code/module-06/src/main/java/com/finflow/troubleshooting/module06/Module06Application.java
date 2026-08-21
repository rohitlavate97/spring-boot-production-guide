package com.finflow.troubleshooting.module06;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy(exposeProxy = true)
public class Module06Application {

    public static void main(String[] args) {
        SpringApplication.run(Module06Application.class, args);
    }
}
