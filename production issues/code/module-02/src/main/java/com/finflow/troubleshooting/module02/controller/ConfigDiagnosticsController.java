package com.finflow.troubleshooting.module02.controller;

import com.finflow.troubleshooting.module02.config.FinFlowCoreProperties;
import com.finflow.troubleshooting.module02.service.EnvironmentInspectorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/config")
public class ConfigDiagnosticsController {

    private final FinFlowCoreProperties properties;
    private final EnvironmentInspectorService environmentInspector;

    public ConfigDiagnosticsController(FinFlowCoreProperties properties,
                                       EnvironmentInspectorService environmentInspector) {
        this.properties = properties;
        this.environmentInspector = environmentInspector;
    }

    @GetMapping("/current")
    public ResponseEntity<Map<String, Object>> getCurrentConfiguration() {
        return ResponseEntity.ok(Map.of(
                "gatewayUrl", properties.getGatewayUrl(),
                "timeoutMs", properties.getTimeoutMs(),
                "maxRetries", properties.getMaxRetries(),
                "apiKeyMasked", "******"
        ));
    }

    @GetMapping("/inspect")
    public ResponseEntity<Map<String, Object>> inspectProperty(@RequestParam String key) {
        return ResponseEntity.ok(environmentInspector.inspectPropertyLineage(key));
    }
}
