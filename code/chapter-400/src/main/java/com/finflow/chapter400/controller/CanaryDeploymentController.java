package com.finflow.chapter400.controller;

import com.finflow.chapter400.model.CanaryVerificationResult;
import com.finflow.chapter400.model.DeploymentVersionInfo;
import com.finflow.chapter400.service.FeatureFlagManager;
import com.finflow.chapter400.service.ReleaseVerificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/deployment")
public class CanaryDeploymentController {

    private final FeatureFlagManager featureFlagManager;
    private final ReleaseVerificationService releaseVerificationService;

    @Value("${finflow.deployment.version:v3.0.0}")
    private String deploymentVersion;

    @Value("${finflow.deployment.environment:production}")
    private String environment;

    @Value("${finflow.deployment.strategy:CANARY_PROGRESSIVE}")
    private String strategy;

    @Value("${finflow.deployment.commit-sha:7ef89ad}")
    private String commitSha;

    public CanaryDeploymentController(FeatureFlagManager featureFlagManager,
                                      ReleaseVerificationService releaseVerificationService) {
        this.featureFlagManager = featureFlagManager;
        this.releaseVerificationService = releaseVerificationService;
    }

    @GetMapping("/version")
    public ResponseEntity<DeploymentVersionInfo> getDeploymentInfo() {
        DeploymentVersionInfo info = new DeploymentVersionInfo(
                deploymentVersion,
                environment,
                strategy,
                commitSha,
                10,
                featureFlagManager.getAllFlagStates()
        );
        return ResponseEntity.ok(info);
    }

    @GetMapping("/features/evaluate")
    public ResponseEntity<Map<String, Object>> evaluateFeature(
            @RequestParam String flagKey,
            @RequestParam(required = false, defaultValue = "user_default") String userId) {
        boolean enabled = featureFlagManager.isFeatureEnabled(flagKey, userId);
        return ResponseEntity.ok(Map.of(
                "flagKey", flagKey,
                "userId", userId,
                "enabled", enabled
        ));
    }

    @PostMapping("/features/toggle")
    public ResponseEntity<Map<String, Object>> updateToggle(
            @RequestParam String flagKey,
            @RequestParam(required = false) Integer percentage,
            @RequestParam(required = false, defaultValue = "false") boolean killSwitch) {

        if (killSwitch) {
            featureFlagManager.triggerEmergencyKillSwitch(flagKey);
            return ResponseEntity.ok(Map.of("status", "KILL_SWITCH_ACTIVE", "flagKey", flagKey));
        }

        if (percentage != null) {
            featureFlagManager.updateRolloutPercentage(flagKey, percentage);
            return ResponseEntity.ok(Map.of("status", "ROLLOUT_UPDATED", "flagKey", flagKey, "percentage", percentage));
        }

        return ResponseEntity.badRequest().body(Map.of("error", "Provide either percentage or killSwitch=true"));
    }

    @PostMapping("/verify-canary")
    public ResponseEntity<CanaryVerificationResult> verifyCanary(
            @RequestParam(defaultValue = "v3.0.0") String version,
            @RequestParam(defaultValue = "20") int trafficWeight,
            @RequestParam(defaultValue = "0.15") double errorRate,
            @RequestParam(defaultValue = "48.0") double p99Latency) {
        CanaryVerificationResult result = releaseVerificationService.verifyCanaryStep(
                version, trafficWeight, errorRate, p99Latency);
        return ResponseEntity.ok(result);
    }
}
