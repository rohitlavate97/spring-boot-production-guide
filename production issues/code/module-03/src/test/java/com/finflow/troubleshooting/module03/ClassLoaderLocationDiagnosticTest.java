package com.finflow.troubleshooting.module03;

import com.finflow.troubleshooting.module03.service.ClassLoaderDiagnosticService;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ClassLoaderLocationDiagnosticTest {

    private final ClassLoaderDiagnosticService diagnosticService = new ClassLoaderDiagnosticService();

    @Test
    void testInspectClassOriginLocatesJarSuccessfully() {
        Map<String, Object> result = diagnosticService.inspectClassOrigin("org.apache.commons.codec.digest.DigestUtils");

        assertThat(result.get("classFound")).isEqualTo(true);
        assertThat(result.get("canonicalName")).isEqualTo("org.apache.commons.codec.digest.DigestUtils");
        assertThat((String) result.get("loadedJarLocation")).contains("commons-codec");
    }

    @Test
    void testInspectMissingClassReturnsClassNotFound() {
        Map<String, Object> result = diagnosticService.inspectClassOrigin("com.nonexistent.LegacyCorruptedEngine");

        assertThat(result.get("classFound")).isEqualTo(false);
        assertThat((String) result.get("error")).contains("ClassNotFoundException");
    }
}
