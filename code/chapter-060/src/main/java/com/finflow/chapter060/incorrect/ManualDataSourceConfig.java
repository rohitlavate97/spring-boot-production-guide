package com.finflow.chapter060.incorrect;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;

/**
 * INCORRECT IMPLEMENTATION
 * 
 * Defining a custom DataSource this way explicitly turns off Spring Boot's
 * DataSourceAutoConfiguration. 
 * 
 * The issue:
 * 1. You lose HikariCP (the default high-performance connection pool).
 * 2. You lose actuator metrics for the database.
 * 3. You lose auto-configured health indicators.
 * 4. Hardcoded properties are poor practice for 12-factor apps.
 */
@Configuration
public class ManualDataSourceConfig {

    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName("org.h2.Driver");
        dataSource.setUrl("jdbc:h2:mem:testdb");
        dataSource.setUsername("sa");
        dataSource.setPassword("password");
        return dataSource;
    }
}
