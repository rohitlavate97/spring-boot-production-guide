# Module 23: File Uploads, Storage Leaks & Ephemeral Containers

## Overview
This module explores Spring Boot file uploads, resolving high-impact production pitfalls: Ephemeral disk exhaustion (`/tmp` fills up leading to Kubernetes `DiskPressure` evictions), JVM heap OutOfMemoryError via `file.getBytes()` buffering, multipart size limit exceptions, deterministic temporary file lifecycle cleanup, and Directory Traversal vulnerability mitigation.

## Key Scenarios Covered
1. **Ephemeral Disk Exhaustion & Pod Eviction:**
   - Why unprocessed/orphaned `/tmp` upload files fill container overlay filesystems, triggering Kubernetes node-level `DiskPressure` and pod evictions.
   - Deterministic temporary file cleanup with `try-finally` and background directory purgers.
2. **JVM Heap OOM on Concurrent Uploads:**
   - Why `multipartFile.getBytes()` or `.readAllBytes()` buffers massive files into memory, exhausting Java heap under concurrency.
   - Solving with true streaming processing (`InputStream` piping via 8KB chunks with $O(1)$ memory).
3. **Path Traversal & Filename Sanitization:**
   - Defending against `../../` injection attacks from untrusted `getOriginalFilename()`.
4. **Clean Error Handling for HTTP 413:**
   - Gracefully handling `MaxUploadSizeExceededException` via `@ControllerAdvice`.

## Project Structure
- `src/main/java/.../service/`:
  - `SecureFileStorageService.java` (Streaming SHA-256 calculation, filename sanitization, auto-cleanup).
- `src/main/java/.../advice/`:
  - `FileUploadExceptionHandler.java` (Global exception handler for 413 Payload Too Large).
- `src/main/java/.../controller/`:
  - `FileUploadDiagnosticsController.java` (REST endpoints for streaming upload, safe temp processing, and sanitization tests).
- `src/test/java/.../`:
  - `StreamingFileUploadTest.java`
  - `FilenameSanitizationTest.java`
  - `TempFileCleanupTest.java`
  - `FileUploadDiagnosticsControllerTest.java`
  - `Module23IntegrationTest.java`

## How to Run Tests
```bash
mvn clean test
```

## Complete Documentation
For the full deep-dive technical incident guide, see [Module 23 Documentation](../../docs/module-23-file-uploads-storage-leaks-ephemeral-containers.md).
