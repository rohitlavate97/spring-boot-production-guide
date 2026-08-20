package com.finflow.chapter310.unit;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class KubernetesManifestValidationTest {

    private final Yaml yaml = new Yaml();

    @Test
    @SuppressWarnings("unchecked")
    public void testDeploymentYaml_containsAllProductionSafeguards() {
        InputStream is = getClass().getResourceAsStream("/k8s/deployment.yaml");
        assertThat(is).isNotNull();

        Map<String, Object> doc = yaml.load(is);
        assertThat(doc.get("kind")).isEqualTo("Deployment");

        Map<String, Object> spec = (Map<String, Object>) doc.get("spec");
        assertThat(spec.get("replicas")).isEqualTo(4);

        // Verify RollingUpdate Strategy: maxUnavailable = 0 for zero-downtime
        Map<String, Object> strategy = (Map<String, Object>) spec.get("strategy");
        assertThat(strategy.get("type")).isEqualTo("RollingUpdate");
        Map<String, Object> rollingUpdate = (Map<String, Object>) strategy.get("rollingUpdate");
        assertThat(rollingUpdate.get("maxUnavailable")).isEqualTo(0);
        assertThat(rollingUpdate.get("maxSurge")).isEqualTo("25%");

        // Verify Pod Template Spec
        Map<String, Object> template = (Map<String, Object>) spec.get("template");
        Map<String, Object> podSpec = (Map<String, Object>) template.get("spec");
        assertThat(podSpec.get("terminationGracePeriodSeconds")).isEqualTo(35);

        List<Map<String, Object>> containers = (List<Map<String, Object>>) podSpec.get("containers");
        assertThat(containers).hasSize(1);
        Map<String, Object> container = containers.get(0);

        // Verify Probes
        Map<String, Object> startupProbe = (Map<String, Object>) container.get("startupProbe");
        assertThat(startupProbe).isNotNull();

        Map<String, Object> livenessProbe = (Map<String, Object>) container.get("livenessProbe");
        assertThat(livenessProbe).isNotNull();

        Map<String, Object> readinessProbe = (Map<String, Object>) container.get("readinessProbe");
        assertThat(readinessProbe).isNotNull();

        // Verify PreStop Hook (sleep 5 for connection drain)
        Map<String, Object> lifecycle = (Map<String, Object>) container.get("lifecycle");
        assertThat(lifecycle).isNotNull();
        Map<String, Object> preStop = (Map<String, Object>) lifecycle.get("preStop");
        Map<String, Object> exec = (Map<String, Object>) preStop.get("exec");
        List<String> command = (List<String>) exec.get("command");
        assertThat(command).contains("/bin/sh", "-c", "sleep 5");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testPdbYaml_specifiesMinAvailable() {
        InputStream is = getClass().getResourceAsStream("/k8s/pdb.yaml");
        assertThat(is).isNotNull();

        Map<String, Object> doc = yaml.load(is);
        assertThat(doc.get("kind")).isEqualTo("PodDisruptionBudget");

        Map<String, Object> spec = (Map<String, Object>) doc.get("spec");
        assertThat(spec.get("minAvailable")).isEqualTo("75%");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testHpaYaml_specifiesAutoscalingBounds() {
        InputStream is = getClass().getResourceAsStream("/k8s/hpa.yaml");
        assertThat(is).isNotNull();

        Map<String, Object> doc = yaml.load(is);
        assertThat(doc.get("kind")).isEqualTo("HorizontalPodAutoscaler");

        Map<String, Object> spec = (Map<String, Object>) doc.get("spec");
        assertThat(spec.get("minReplicas")).isEqualTo(4);
        assertThat(spec.get("maxReplicas")).isEqualTo(20);
    }
}
