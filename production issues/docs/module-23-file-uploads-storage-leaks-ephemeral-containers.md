# Module 23: File Uploads, Storage Leaks & Ephemeral Containers

## Issue 23.1: Ephemeral Disk Exhaustion (DiskPressure Eviction), Heap OOM via `file.getBytes()`, and Path Traversal Hazards

---

### 1. Scenario

During peak merchant onboarding on the **FinFlow Merchant KYC & Proof-of-Identity Upload Portal**:
1. Prospective merchants submitted high-resolution PDF identity documents, company incorporation files, and audit zip archives (up to 50MB each).
2. The upload controller handled files using the common anti-pattern: `byte[] fileBytes = multipartFile.getBytes();` or `multipartFile.getInputStream().readAllBytes();` to compute SHA-256 integrity hashes.
3. When **40 merchants uploaded documents concurrently**, the JVM attempted to allocate **2.0GB of contiguous heap space in seconds**. The Young Generation was instantly overwhelmed, triggering catastrophic **Full GC pauses lasting 8–14 seconds**, socket timeouts across downstream payment APIs, and eventual `java.lang.OutOfMemoryError: Java heap space` (**The Heap Buffer OOM Disaster**).
4. Simultaneously, a document virus-scanning service copied uploaded files to the local container disk using `file.transferTo(new File("/tmp/" + name))`. Because the code lacked guaranteed `finally` cleanup, over **45GB of temporary files accumulated in `/tmp` over 3 days**.
5. The container ran inside Kubernetes without an isolated volume mount. The node's root filesystem reached 96% capacity. Kubelet marked the node with **`DiskPressure: True`** and began **forcefully evicting and terminating all banking and settlement pods on that node** (**The Ephemeral Disk Exhaustion Eviction**).
6. To make matters worse, a penetration test submitted a file named `../../../../etc/cron.d/malicious_job`. Because the service used raw `multipartFile.getOriginalFilename()`, the file was written directly into the host cron directory (**Directory Traversal Vulnerability**).

---

### 2. Symptoms

```text
1. Kubernetes Pod Evictions via DiskPressure:
   kubectl get nodes -> STATUS: Ready, DiskPressure
   Pod status: Evicted (Message: "The node had condition: [DiskPressure]")

2. Massive Heap Memory Spikes & OutOfMemoryError on Concurrent Uploads:
   java.lang.OutOfMemoryError: Java heap space at org.springframework.web.multipart...
   Heap profiler shows 85% of memory consumed by byte[] arrays.

3. Ephemeral Disk Growth in Container Filesystem:
   df -h /tmp shows 100% disk usage.
   lsof +L1 shows open file descriptors for unlinked temporary files that cannot be reclaimed.

4. Abrupt TCP Connection Resets (ECONNRESET) on Large Files:
   Clients receive ERR_CONNECTION_RESET instead of HTTP 413 Payload Too Large because
   Tomcat terminates the socket abruptly before the error response completes.

5. Arbitrary File Overwrites (Path Injection):
   Files written outside designated storage directories due to ../ in original filename.
```

---

### 3. Possible Root Causes

1. **Heap Buffering via `getBytes()` / `readAllBytes()`:** Loading entire multipart file payloads into JVM heap memory instead of streaming data in fixed-size chunks ($O(1)$ memory).
2. **Missing Deterministic Temp File Cleanup:** Relying on JVM shutdown hooks or garbage collection to delete temporary upload files rather than using `try-finally` blocks.
3. **Unbounded Container Root Filesystem:** Writing temporary files directly to the container root filesystem rather than an isolated, size-limited Kubernetes `emptyDir` volume.
4. **Unsanitized `getOriginalFilename()`:** Using client-provided filenames directly in filesystem path construction without stripping path traversal sequences (`../`).
5. **Missing Global `@ControllerAdvice` for `MaxUploadSizeExceededException`:** Allowing unhandled size limit exceptions to bubble up to the servlet container.

---

### 4. Architecture Context: Streaming Upload vs Heap Buffering & Ephemeral Volume Mounts

```text
┌─────────────────────────────────────────────────────────────────────────────────────────────────┐
│                     STREAMING VS BUFFERED MULTIPART PROCESSING                                  │
│                                                                                                 │
│  ❌ HEAP BUFFERING ANTI-PATTERN (Memory: O(N * Concurrency)):                                   │
│  Client (50MB Upload) ──► multipartFile.getBytes() ──► Allocates 50MB byte[] in JVM Heap!       │
│  40 Concurrent Uploads ──► 40 * 50MB = 2.0GB Immediate Heap Allocation ──► OutOfMemoryError!    │
│                                                                                                 │
│  ✅ STREAMING PROCESSING (Memory: O(1) - Constant 8KB Buffer):                                  │
│  Client ──► InputStream ──► [8KB Chunk Buffer] ──► MessageDigest (SHA-256) ──► Blob Store       │
│  40 Concurrent Uploads ──► 40 * 8KB = 320KB Total Memory! (Zero GC Thrashing!)                  │
│                                                                                                 │
│  ┌───────────────────────────────────────────────────────────────────────────────────────────┐  │
│  │ KUBERNETES EPHEMERAL STORAGE ISOLATION:                                                   │  │
│  │                                                                                           │  │
│  │   Pod Spec:                                                                               │  │
│  │     volumeMounts:                                                                         │  │
│  │       - mountPath: /app/uploads/tmp                                                       │  │
│  │         name: upload-scratch-space                                                        │  │
│  │     volumes:                                                                              │  │
│  │       - name: upload-scratch-space                                                        │  │
│  │         emptyDir:                                                                         │  │
│  │           sizeLimit: 5Gi  # Protects node root filesystem from DiskPressure!              │  │
│  └───────────────────────────────────────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────────────────────────────────┘
```

---

### 5. How to Reproduce the Issues

#### ❌ Anti-Pattern 1: Buffering Entire Upload into Heap Memory
```java
// ❌ FATAL ANTI-PATTERN: Loads entire 50MB into JVM heap memory!
@PostMapping("/upload")
public ResponseEntity<?> upload(@RequestParam("file") MultipartFile file) throws IOException {
    byte[] data = file.getBytes(); // 2GB heap allocation under 40 concurrent users!
    String hash = DigestUtils.sha256Hex(data);
    return ResponseEntity.ok(hash);
}
```

#### ❌ Anti-Pattern 2: Temp File Leak Without `finally` Cleanup
```java
// ❌ ANTI-PATTERN: If processing throws exception, temp file is orphaned in /tmp forever!
public void processDoc(MultipartFile file) throws IOException {
    File temp = new File("/tmp/" + file.getOriginalFilename());
    file.transferTo(temp);
    virusScanService.scan(temp); // Throws exception -> File is NEVER deleted!
    temp.delete();
}
```

#### ❌ Anti-Pattern 3: Path Traversal Vulnerability
```java
// ❌ CRITICAL SECURITY VULNERABILITY (CWE-22): Attacker sends filename="../../etc/passwd"
public void saveToDisk(MultipartFile file) throws IOException {
    File destination = new File("/var/app/uploads/" + file.getOriginalFilename());
    file.transferTo(destination); // Writes to arbitrary locations on the host!
}
```

---

### 6. Evidence Collection & Diagnostic Probing

#### Method 1: Check Ephemeral Disk Usage in Container
```bash
# Check disk utilization on root and tmp mounts
df -h

# Find large uncleaned temp files older than 1 hour
find /tmp -type f -name "upload_*" -mmin +60 -ls
```

#### Method 2: Detect Open Unlinked Leaks via `lsof`
```bash
# Files deleted by filesystem but still held open by JVM processes
lsof +L1
```

#### Method 3: Inspect Heap for Excessive `byte[]` Allocation
```bash
jcmd <PID> GC.class_histogram | grep "\[B"
```

---

### 7. Step-by-Step Debugging Procedure

```text
Step 1: Inspect Disk Space & Kubernetes Node Conditions.
        Run `kubectl describe node` to see if DiskPressure triggered pod evictions.

Step 2: Switch All Multipart Processing to Streaming.
        Pipe `multipartFile.getInputStream()` using fixed 8KB buffers; eliminate all `.getBytes()`.

Step 3: Wrap All Temporary File Operations in Try-Finally / Try-with-Resources.
        Guarantee `Files.deleteIfExists(tempFile)` executes even when processing fails.

Step 4: Sanitize Filenames Against Directory Traversal.
        Extract only `Paths.get(name).getFileName().toString()` and strip illegal characters.

Step 5: Mount Dedicated `emptyDir` Volumes with `sizeLimit` in Kubernetes.
        Isolate upload temp directories so container leaks cannot crash the host Kubernetes node.
```

---

### 8. Technical Root Cause Deep-Dive

#### 1. Why `file.getBytes()` Causes JVM Garbage Collection Death Spirals
- Calling `file.getBytes()` creates a contiguous primitive `byte[]` on the Java heap.
- For a 50MB upload, the JVM must allocate a single 50MB array. If 40 requests arrive concurrently:
  $$\text{Heap Allocation} = 40 \times 50\text{MB} = 2,000\text{MB}$$
- Large arrays cannot be partitioned across non-contiguous heap regions. If the Young Gen lacks contiguous space, objects promote directly to the Old Generation.
- Old Gen rapidly saturates, triggering frequent stop-the-world Full GCs that freeze application threads.

#### 2. The Mechanics of Kubernetes `DiskPressure` Eviction
- The Kubelet monitors filesystem usage on the node root filesystem and container runtime storage.
- When free inode or disk space drops below the eviction threshold (e.g. `imageGCHighThresholdPercent: 85%` or `nodefs.available < 10%`):
- Kubelet sets `NodeCondition: DiskPressure = True`.
- Kubelet forcefully evicts BestEffort and Burstable pods until disk usage drops below thresholds.

#### 3. Path Traversal & Filename Sanitization
- RFC 7578 allows `Content-Disposition: form-data; name="file"; filename="../../../../etc/cron.d/job"`.
- Without sanitization, `new File(uploadDir, filename)` resolves to `/etc/cron.d/job`, granting arbitrary code execution.
- Using `Paths.get(rawFilename).getFileName().toString()` discards all directory components, safely extracting only the base name.

---

### 9. Production-Grade Fixes

#### ✅ Fix 1: Streaming File Processing Service (`SecureFileStorageService.java`)
```java
@Service
public class SecureFileStorageService {

    public StreamResult streamAndCalculateHash(InputStream inputStream) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192]; // Fixed 8KB chunk buffer (O(1) memory)
            int bytesRead;
            long totalBytes = 0;

            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
                totalBytes += bytesRead;
            }

            String checksum = HexFormat.of().formatHex(digest.digest());
            return new StreamResult(checksum, totalBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String sanitizeFilename(String rawFilename) {
        if (rawFilename == null || rawFilename.isBlank()) {
            return "file_" + UUID.randomUUID();
        }
        String baseName = Paths.get(rawFilename).getFileName().toString();
        return baseName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
```

#### ✅ Fix 2: Global 413 Exception Handler (`FileUploadExceptionHandler.java`)
```java
@ControllerAdvice
public class FileUploadExceptionHandler {

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of(
                "timestamp", Instant.now(),
                "status", 413,
                "error", "Payload Too Large",
                "message", "Uploaded file exceeds the maximum allowed size (25MB)"
        ));
    }
}
```

#### ✅ Fix 3: Kubernetes Pod `emptyDir` Volume Spec
```yaml
spec:
  containers:
    - name: finflow-kyc-service
      image: finflow/kyc-service:latest
      volumeMounts:
        - mountPath: /app/uploads/tmp
          name: upload-scratch
      resources:
        requests:
          ephemeral-storage: "1Gi"
        limits:
          ephemeral-storage: "5Gi"
  volumes:
    - name: upload-scratch
      emptyDir:
        sizeLimit: "5Gi"
```

---

### 10. Verification

1. **Streaming Hash Test:** Run `StreamingFileUploadTest.java` to verify streaming SHA-256 computation with 8KB buffer without heap allocation spikes.
2. **Filename Sanitization Test:** Run `FilenameSanitizationTest.java` to verify neutralization of `../../` directory traversal payloads.
3. **Temp File Auto-Cleanup Test:** Run `TempFileCleanupTest.java` to verify temporary files are deleted in `finally` blocks.
4. **Controller API Test:** Run `FileUploadDiagnosticsControllerTest.java` to test REST multipart endpoints and 413 handling.
5. **Integration Test:** Run `Module23IntegrationTest.java` to verify Spring Boot context and Actuator health.

---

### 11. Prevention & Production Readiness

1. **Rule: Never Call `file.getBytes()` on User-Provided Files:**
   Always stream file contents via `InputStream` with bounded buffers.
2. **Rule: Always Isolate Ephemeral Storage in Kubernetes:**
   Define `emptyDir.sizeLimit` and `ephemeral-storage` resource limits on all file-handling pods.
3. **Prometheus Alerting Rule for Ephemeral Disk Space:**
```yaml
- alert: ContainerEphemeralDiskNearFull
  expr: (container_fs_usage_bytes{container="finflow-kyc-service"} / container_fs_limit_bytes) * 100 > 80
  for: 3m
  labels:
    severity: warning
  annotations:
    summary: "Container ephemeral upload scratch disk is at {{ $value }}% capacity"
```

---

### 12. Interview & Production Questions

#### Interview Questions
1. **Q: Why does calling `multipartFile.getBytes()` cause OutOfMemoryErrors in production?**
   *Answer:* `getBytes()` loads the entire file payload into a contiguous `byte[]` on the JVM heap. Under concurrent uploads, multiple large byte arrays saturate the Eden/Tenured heap spaces, causing catastrophic GC pauses and OOMs. Streaming via `InputStream` with small 8KB buffers consumes $O(1)$ memory.
2. **Q: How does Kubernetes `DiskPressure` affect running pods, and how do you protect against it?**
   *Answer:* When a node's filesystem fills up (e.g. from uncleaned temp upload files), Kubelet sets `DiskPressure: True` and evicts pods. Mounting dedicated `emptyDir` volumes with `sizeLimit` isolates temporary files so a leak only terminates the offending pod without crashing the host node.
3. **Q: What is a Directory Traversal attack in the context of `MultipartFile.getOriginalFilename()`?**
   *Answer:* Attackers can craft HTTP multipart requests where `filename` contains `../../../../etc/shadow`. If passed directly to filesystem constructors, files are written outside the intended upload directory. Sanitization with `Paths.get(filename).getFileName().toString()` prevents this.
4. **Q: What is the purpose of `spring.servlet.multipart.file-size-threshold`?**
   *Answer:* It defines the memory threshold (e.g. 2MB). Uploads smaller than this threshold remain in JVM memory for low-latency processing, while uploads larger than this threshold are written to disk to protect JVM heap memory.
5. **Q: Why do clients often see `ERR_CONNECTION_RESET` instead of HTTP 413 when uploading an oversized file?**
   *Answer:* If the server rejects the request while the client is still sending the multipart body, the TCP socket may be closed abruptly by the servlet container before the HTTP 413 response is acknowledged. Configuring proper Tomcat max-swallow-size prevents connection resets.

#### Production Incident Questions
1. **Incident:** A KYC service crashed with `OutOfMemoryError: Java heap space` when 30 users uploaded identity videos. What happened?
   *Diagnosis:* The controller called `multipartFile.getBytes()`, attempting to allocate several gigabytes of heap memory simultaneously. Fix: Switch to streaming `InputStream` processing with an 8KB buffer.
2. **Incident:** All microservices running on Kubernetes Node 4 were evicted with `NodeHasDiskPressure`. Why?
   *Diagnosis:* A file upload service wrote temporary files to `/tmp` without deleting them in `finally` blocks, filling the host root filesystem. Fix: Use deterministic `try-finally` deletion, background cleanup, and mount `emptyDir` with `sizeLimit`.
3. **Incident:** An attacker successfully overwrote a configuration file in `/etc/app/config.json` via the profile picture upload API. Why?
   *Diagnosis:* Path traversal vulnerability (CWE-22) via unsanitized `getOriginalFilename()`. Fix: Use `Paths.get(raw).getFileName().toString()` and sanitize with regex allowlists.
4. **Incident:** Uploading a 28MB file causes an immediate error before the controller method is executed. Why?
   *Diagnosis:* Spring's `spring.servlet.multipart.max-file-size` was set to 25MB. Fix: Increase limit or handle `MaxUploadSizeExceededException` in `@ControllerAdvice` to return a clear 413 error.
5. **Incident:** Temp files were deleted in code, but `df -h` still showed 100% disk usage. Why?
   *Diagnosis:* The files were unlinked from the filesystem but still held open by an active JVM `InputStream` (`lsof +L1`). Fix: Ensure input streams are closed in `try-with-resources`.

#### Trick Questions
1. **Trick:** Does `File.deleteOnExit()` reliably clean up temporary files in production containers?
   *Answer:* No! `deleteOnExit()` only runs when the JVM shuts down normally. During normal long-running operations, files accumulate indefinitely until disk space is exhausted. If the container is killed with `SIGKILL` or OOMKilled, the hook never runs.
2. **Trick:** If `max-file-size` is 20MB and `max-request-size` is 20MB, can you upload a 20MB file?
   *Answer:* No! The multipart request body includes MIME boundary headers and form field metadata, making the total request size slightly larger than 20MB. `max-request-size` should always be larger than `max-file-size` (e.g. 25MB).
3. **Trick:** Does `MultipartFile.isEmpty()` check whether the file exists on the client's hard drive?
   *Answer:* No, it only checks if the uploaded file payload contains 0 bytes or if no file was selected in the form.

---

*(Answers and detailed explanations are provided in the Master Answer Guide)*
