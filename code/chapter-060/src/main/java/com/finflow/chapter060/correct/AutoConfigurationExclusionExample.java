package com.finflow.chapter060.correct;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Configuration;

/**
 * CORRECT IMPLEMENTATION
 * 
 * If you truly want to disable an auto-configuration class, 
 * exclude it explicitly rather than hoping a custom bean turns it off.
 * 
 * Alternative 1 (Annotation):
 * @SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
 * 
 * Alternative 2 (Property - BEST for different environments):
 * spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
 */
@Configuration
// Only for example purposes. Normally done on @SpringBootApplication
@EnableAutoConfiguration(exclude = {DataSourceAutoConfiguration.class})
public class AutoConfigurationExclusionExample {
    
    // Custom data source setup can safely go here knowing that 
    // Spring Boot won't try to auto-configure one.
}
