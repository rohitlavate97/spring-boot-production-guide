package com.finflow.troubleshooting.module23;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class FileUploadDiagnosticsControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/v1/files/upload-stream processes file successfully")
    void testUploadStream() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "statement.pdf", "application/pdf",
                "Sample Bank Statement Content 2026".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/files/upload-stream").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("STREAM_PROCESSED_SUCCESS"))
                .andExpect(jsonPath("$.sha256Checksum").exists())
                .andExpect(jsonPath("$.memoryMode").value("O(1)_STREAMING_NO_HEAP_BUFFER"));
    }

    @Test
    @DisplayName("GET /api/v1/files/sanitize cleans up dangerous paths")
    void testSanitizeEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/files/sanitize").param("filename", "../../etc/shadow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sanitized").value("shadow"))
                .andExpect(jsonPath("$.isTraversalNeutralized").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/files/stats returns metrics")
    void testGetStats() throws Exception {
        mockMvc.perform(get("/api/v1/files/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.filesUploaded").exists())
                .andExpect(jsonPath("$.tempFilesCleaned").exists());
    }
}
