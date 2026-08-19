package com.finflow.chapter060.incorrect;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;

/**
 * INCORRECT IMPLEMENTATION
 * 
 * 1. Uses @Configuration instead of @AutoConfiguration.
 *    In Spring Boot 3.x, auto-configurations should use @AutoConfiguration.
 * 2. Missing ordering. It doesn't specify if it should run after DataSourceAutoConfiguration.
 * 3. Uses @ConditionalOnBean(DataSource.class) incorrectly.
 *    Conditions are evaluated BEFORE beans are created. If this runs before 
 *    DataSourceAutoConfiguration, the DataSource bean won't exist yet, and 
 *    this condition will fail!
 */
@Configuration
@ConditionalOnBean(DataSource.class)
public class BrokenAutoConfiguration {

    @Bean
    public JdbcTemplate customJdbcTemplate(DataSource dataSource) {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.setFetchSize(100);
        return jdbcTemplate;
    }
}
