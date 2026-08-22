package com.finflow.troubleshooting.module23;

import com.finflow.troubleshooting.module23.service.SecureFileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FilenameSanitizationTest {

    private SecureFileStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new SecureFileStorageService("./target/test-uploads-tmp", 60);
    }

    @Test
    @DisplayName("Should neutralize Unix and Windows path traversal attacks")
    void testPathTraversalNeutralization() {
        String attack1 = "../../../../etc/passwd";
        String cleaned1 = storageService.sanitizeFilename(attack1);
        assertThat(cleaned1).isEqualTo("passwd");
        assertThat(cleaned1).doesNotContain("..");
        assertThat(cleaned1).doesNotContain("/");

        String attack2 = "..\\..\\Windows\\System32\\cmd.exe";
        String cleaned2 = storageService.sanitizeFilename(attack2);
        assertThat(cleaned2).isEqualTo("cmd.exe");
        assertThat(cleaned2).doesNotContain("..");
        assertThat(cleaned2).doesNotContain("\\");
    }

    @Test
    @DisplayName("Should replace illegal and special characters with safe underscores")
    void testSpecialCharacterSanitization() {
        String dangerousName = "invoice*2026?_final#$@!.pdf";
        String cleaned = storageService.sanitizeFilename(dangerousName);
        assertThat(cleaned).isEqualTo("invoice_2026__final_____.pdf");
    }
}
