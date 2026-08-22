package com.finflow.troubleshooting.module23.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

@Service
public class SecureFileStorageService {

    private static final Logger log = LoggerFactory.getLogger(SecureFileStorageService.class);

    public record StreamResult(String sha256Checksum, long totalBytes) {}

    private final Path tempStoragePath;
    private final long maxFileAgeMinutes;

    private final AtomicLong filesUploaded = new AtomicLong(0);
    private final AtomicLong totalBytesStreamed = new AtomicLong(0);
    private final AtomicLong tempFilesCleaned = new AtomicLong(0);
    private final AtomicLong pathTraversalAttemptsBlocked = new AtomicLong(0);

    public SecureFileStorageService(
            @Value("${finflow.storage.temp-dir:./target/finflow-uploads-tmp}") String tempDir,
            @Value("${finflow.storage.max-file-age-minutes:60}") long maxFileAgeMinutes
    ) {
        this.tempStoragePath = Paths.get(tempDir).toAbsolutePath().normalize();
        this.maxFileAgeMinutes = maxFileAgeMinutes;
        try {
            Files.createDirectories(this.tempStoragePath);
        } catch (IOException e) {
            log.error("Failed to create temporary upload directory: {}", this.tempStoragePath, e);
        }
    }

    /**
     * ✅ PRODUCTION FIX 1: Filename Sanitization & Directory Traversal Prevention
     * Strips `../` path injection payloads and unsafe characters.
     */
    public String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            return "unnamed_file_" + UUID.randomUUID();
        }

        // Extract base filename without path components
        Path path = Paths.get(rawFilename);
        String baseName = path.getFileName().toString();

        if (rawFilename.contains("..") || rawFilename.contains("/") || rawFilename.contains("\\")) {
            pathTraversalAttemptsBlocked.incrementAndGet();
            log.warn("[PATH TRAVERSAL BLOCKED] Suspicious filename '{}' sanitized to '{}'", rawFilename, baseName);
        }

        // Replace any remaining non-alphanumeric/non-extension chars
        String cleaned = baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
        return cleaned.isBlank() ? "file_" + UUID.randomUUID() : cleaned;
    }

    /**
     * ✅ PRODUCTION FIX 2: Streaming Processing with Zero Heap Buffering (O(1) Memory Footprint)
     * Streams via 8KB chunks directly to MessageDigest without loading entire file into JVM heap.
     */
    public StreamResult streamAndCalculateHash(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            filesUploaded.incrementAndGet();
            totalBytesStreamed.addAndGet(totalBytes);

            String checksum = HexFormat.of().formatHex(digest.digest());
            log.info("[STREAM SUCCESS] Processed {} bytes. SHA-256={}", totalBytes, checksum);
            return new StreamResult(checksum, totalBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest algorithm not found", e);
        }
    }

    /**
     * ✅ PRODUCTION FIX 3: Deterministic Temp File Cleanup via Try-Finally
     */
    public void processWithAutoCleanup(MultipartFile file, Consumer<Path> consumer) throws IOException {
        String sanitized = sanitizeFilename(file.getOriginalFilename());
        Path tempFile = Files.createTempFile(tempStoragePath, "upload_" + sanitized + "_", ".tmp");

        try {
            file.transferTo(tempFile.toFile());
            consumer.accept(tempFile);
        } finally {
            try {
                if (Files.deleteIfExists(tempFile)) {
                    tempFilesCleaned.incrementAndGet();
                    log.info("[CLEANUP SUCCESS] Deleted temporary upload file: {}", tempFile);
                }
            } catch (IOException e) {
                log.error("[CLEANUP FAILED] Could not delete temporary file: {}", tempFile, e);
            }
        }
    }

    /**
     * ✅ PRODUCTION FIX 4: Background Cleaner for Orphaned Temp Files
     */
    public int purgeOrphanedTempFiles() {
        int purgedCount = 0;
        if (!Files.exists(tempStoragePath)) return 0;

        Instant cutoff = Instant.now().minusSeconds(maxFileAgeMinutes * 60);

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempStoragePath)) {
            for (Path path : stream) {
                if (Files.isRegularFile(path)) {
                    BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                    if (attrs.lastModifiedTime().toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(path);
                        purgedCount++;
                        tempFilesCleaned.incrementAndGet();
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error during temp file purge", e);
        }
        return purgedCount;
    }

    public Path getTempStoragePath() {
        return tempStoragePath;
    }

    public Map<String, Object> getStorageStats() {
        return Map.of(
                "tempStoragePath", tempStoragePath.toString(),
                "filesUploaded", filesUploaded.get(),
                "totalBytesStreamed", totalBytesStreamed.get(),
                "tempFilesCleaned", tempFilesCleaned.get(),
                "pathTraversalAttemptsBlocked", pathTraversalAttemptsBlocked.get()
        );
    }
}
