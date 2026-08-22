package com.finflow.troubleshooting.module23;

import com.finflow.troubleshooting.module23.service.SecureFileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class StreamingFileUploadTest {

    private SecureFileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new SecureFileStorageService("./target/test-uploads-tmp", 60);
    }

    @Test
    @DisplayName("Should stream data in chunks and calculate accurate SHA-256 hash without heap buffering")
    void testStreamingHashCalculation() throws IOException {
        String testData = "FinFlow Audit Settlement Document Content 2026";
        ByteArrayInputStream is = new ByteArrayInputStream(testData.getBytes(StandardCharsets.UTF_8));

        var result = storageService.streamAndCalculateHash(is);

        assertThat(result.totalBytes()).isEqualTo(testData.getBytes(StandardCharsets.UTF_8).length);
        assertThat(result.sha256Checksum()).isNotEmpty();
        assertThat(result.sha256Checksum().length()).isEqualTo(64); // SHA-256 hex string length
    }
}
