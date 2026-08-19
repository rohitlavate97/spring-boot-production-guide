package com.finflow.chapter060.correct;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Properties class bound to inflow.payment.gateway.*
 * 
 * Note: While Java 16+ records can be used with @ConstructorBinding, 
 * standard JavaBean classes with getters and setters are often still used 
 * for simpler @ConfigurationProperties mapping, especially when allowing Spring 
 * to handle defaults easily or mutable configs.
 */
@Validated
@ConfigurationProperties(prefix = "finflow.payment.gateway")
public class PaymentGatewayProperties {

    /**
     * Whether the payment gateway integration is enabled.
     */
    private boolean enabled = true;

    /**
     * The base URL of the payment gateway API.
     */
    @NotBlank(message = "Base URL must not be blank when enabled")
    private String baseUrl;

    /**
     * Connection timeout in milliseconds.
     */
    @Min(value = 100, message = "Connect timeout must be at least 100ms")
    private int connectTimeoutMs = 3000;

    /**
     * Read timeout in milliseconds.
     */
    @Min(value = 100, message = "Read timeout must be at least 100ms")
    private int readTimeoutMs = 5000;

    /**
     * Maximum number of retry attempts for transient errors.
     */
    @Max(value = 10, message = "Max retries cannot exceed 10")
    private int maxRetries = 3;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getConnectTimeoutMs() {
        return connectTimeoutMs;
    }

    public void setConnectTimeoutMs(int connectTimeoutMs) {
        this.connectTimeoutMs = connectTimeoutMs;
    }

    public int getReadTimeoutMs() {
        return readTimeoutMs;
    }

    public void setReadTimeoutMs(int readTimeoutMs) {
        this.readTimeoutMs = readTimeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
