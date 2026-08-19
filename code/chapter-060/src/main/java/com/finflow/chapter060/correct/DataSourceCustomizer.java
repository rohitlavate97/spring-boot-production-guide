package com.finflow.chapter060.correct;

import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.zaxxer.hikari.HikariDataSource;
import javax.sql.DataSource;

/**
 * CORRECT IMPLEMENTATION
 * 
 * Instead of creating a DataSource from scratch and killing auto-configuration,
 * you can use the builder or let Spring configure the basics and just adjust properties.
 * 
 * The BEST way is typically via application.yml:
 * spring.datasource.hikari.maximum-pool-size=20
 * spring.datasource.hikari.connection-timeout=5000
 * 
 * If you MUST do it in code, use the auto-configured properties to build the pool
 * so you don't lose the magic of DataSourceProperties auto-binding.
 */
@Configuration
public class DataSourceCustomizer {

    /**
     * This relies on DataSourceProperties which is auto-configured by Spring Boot.
     * It allows us to define the DataSource while still honoring properties like
     * spring.datasource.url, etc.
     */
    // Uncommenting this bean will take over DataSource creation, but in a safe way.
    /*
    @Bean
    public DataSource dataSource(DataSourceProperties properties) {
        HikariDataSource dataSource = properties.initializeDataSourceBuilder().type(HikariDataSource.class).build();
        // Custom programmatic adjustments that couldn't be done via properties
        dataSource.setLeakDetectionThreshold(2000);
        return dataSource;
    }
    */
}
