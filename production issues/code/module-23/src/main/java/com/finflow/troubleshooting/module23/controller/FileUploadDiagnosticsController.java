package com.finflow.troubleshooting.module23.controller;

import com.finflow.troubleshooting.module23.service.SecureFileStorageService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/files")
public class FileUploadDiagnosticsController {

    private final SecureFileStorageService storageService;

    public FileUploadDiagnosticsController(SecureFileStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(value = "/upload-stream", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadStream(@RequestParam("file") MultipartFile file) throws IOException {
        String sanitizedFilename = storageService.sanitizeFilename(file.getOriginalFilename());
        var result = storageService.streamAndCalculateHash(file.getInputStream());

        return ResponseEntity.ok(Map.of(
                "status", "STREAM_PROCESSED_SUCCESS",
                "originalFilename", file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown",
                "sanitizedFilename", sanitizedFilename,
                "bytesRead", result.totalBytes(),
                "sha256Checksum", result.sha256Checksum(),
                "memoryMode", "O(1)_STREAMING_NO_HEAP_BUFFER"
        ));
    }

    @PostMapping(value = "/upload-safe-temp", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadSafeTemp(@RequestParam("file") MultipartFile file) throws IOException {
        String sanitized = storageService.sanitizeFilename(file.getOriginalFilename());

        storageService.processWithAutoCleanup(file, tempPath -> {
            // Simulated audit document scanning / virus scanning
        });

        return ResponseEntity.ok(Map.of(
                "status", "TEMP_PROCESSED_AND_DELETED",
                "filename", sanitized,
                "cleanupVerified", true
        ));
    }

    @GetMapping("/sanitize")
    public ResponseEntity<Map<String, Object>> sanitizeFilename(@RequestParam String filename) {
        String cleaned = storageService.sanitizeFilename(filename);
        return ResponseEntity.ok(Map.of(
                "input", filename,
                "sanitized", cleaned,
                "isTraversalNeutralized", !cleaned.contains("..") && !cleaned.contains("/")
        ));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(storageService.getStorageStats());
    }

    @PostMapping("/purge-temp")
    public ResponseEntity<Map<String, Object>> purgeTemp() {
        int purged = storageService.purgeOrphanedTempFiles();
        return ResponseEntity.ok(Map.of("status", "PURGE_COMPLETED", "filesPurged", purged));
    }
}
