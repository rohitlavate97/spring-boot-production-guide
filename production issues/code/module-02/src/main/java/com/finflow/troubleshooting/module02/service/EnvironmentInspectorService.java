package com.finflow.troubleshooting.module02.service;

import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class EnvironmentInspectorService {

    private final ConfigurableEnvironment environment;

    public EnvironmentInspectorService(ConfigurableEnvironment environment) {
        this.environment = environment;
    }

    public Map<String, Object> inspectPropertyLineage(String propertyKey) {
        String activeValue = environment.getProperty(propertyKey);
        List<Map<String, String>> sourceChain = new ArrayList<>();

        for (PropertySource<?> propertySource : environment.getPropertySources()) {
            if (propertySource.containsProperty(propertyKey)) {
                sourceChain.add(Map.of(
                        "propertySourceName", propertySource.getName(),
                        "value", String.valueOf(propertySource.getProperty(propertyKey))
                ));
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("propertyKey", propertyKey);
        result.put("winningActiveValue", activeValue != null ? maskIfSensitive(propertyKey, activeValue) : null);
        result.put("winningSource", sourceChain.isEmpty() ? "NONE" : sourceChain.get(0).get("propertySourceName"));
        result.put("evaluatedPropertySourcesInPrecedenceOrder", sourceChain);

        return result;
    }

    private String maskIfSensitive(String key, String value) {
        String lower = key.toLowerCase();
        if (lower.contains("key") || lower.contains("secret") || lower.contains("password") || lower.contains("token")) {
            return "****** (REDACTED)";
        }
        return value;
    }
}
