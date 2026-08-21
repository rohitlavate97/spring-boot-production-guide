package com.finflow.troubleshooting.module02.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "finflow.core")
@Validated
public class FinFlowCoreProperties {

    @NotBlank(message = "gateway-url must not be blank")
    private String gatewayUrl;

    @Min(value = 100, message = "timeout-ms must be at least 100ms")
    @Max(value = 30000, message = "timeout-ms must not exceed 30000ms")
    private int timeoutMs;

    @Min(value = 0, message = "max-retries must be positive or zero")
    @Max(value = 5, message = "max-retries cannot exceed 5")
    private int maxRetries;

    @NotBlank(message = "api-key must not be blank")
    private String apiKey;

    public String getGatewayUrl() {
        return gatewayUrl;
    }

    public void setGatewayUrl(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }
}
