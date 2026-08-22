package com.finflow.troubleshooting.module23;

import com.finflow.troubleshooting.module23.service.SecureFileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class TempFileCleanupTest {

    private SecureFileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new SecureFileStorageService("./target/test-cleanup-tmp", 60);
    }

    @Test
    @DisplayName("Temporary files MUST be deleted in finally block after processing completes")
    void testTempFileAutoCleanup() throws IOException {
        MockMultipartFile mockFile = new MockMultipartFile(
                "file", "merchant_kyc.pdf", "application/pdf",
                "Mock KYC Data".getBytes(StandardCharsets.UTF_8)
        );

        AtomicReference<Path> createdTempPath = new AtomicReference<>();

        storageService.processWithAutoCleanup(mockFile, tempPath -> {
            createdTempPath.set(tempPath);
            assertThat(Files.exists(tempPath)).isTrue();
        });

        // After processWithAutoCleanup returns, file MUST be deleted!
        assertThat(createdTempPath.get()).isNotNull();
        assertThat(Files.exists(createdTempPath.get())).isFalse();
    }
}
