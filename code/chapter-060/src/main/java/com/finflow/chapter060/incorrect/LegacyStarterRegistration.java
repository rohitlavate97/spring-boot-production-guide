package com.finflow.chapter060.incorrect;

import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * INCORRECT IMPLEMENTATION
 * 
 * This class is a valid auto-configuration class, but the developer 
 * registered it in META-INF/spring.factories.
 * 
 * Prior to Spring Boot 2.7 (and completely removed in Spring Boot 3.0),
 * auto-configurations were registered in META-INF/spring.factories like:
 * org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
 *   com.finflow.chapter060.incorrect.LegacyStarterRegistration
 * 
 * In Spring Boot 3.x, this file is ignored for auto-configuration!
 * It MUST be registered in:
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 */
@AutoConfiguration
public class LegacyStarterRegistration {
    // Beans would be defined here
}
