package com.finflow.troubleshooting.module03.controller;

import com.finflow.troubleshooting.module03.service.ChecksumSignatureService;
import com.finflow.troubleshooting.module03.service.ClassLoaderDiagnosticService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/dependencies")
public class DependencyDiagnosticsController {

    private final ChecksumSignatureService signatureService;
    private final ClassLoaderDiagnosticService classLoaderDiagnostics;

    public DependencyDiagnosticsController(ChecksumSignatureService signatureService,
                                           ClassLoaderDiagnosticService classLoaderDiagnostics) {
        this.signatureService = signatureService;
        this.classLoaderDiagnostics = classLoaderDiagnostics;
    }

    @PostMapping("/sign")
    public ResponseEntity<Map<String, Object>> signPayload(@RequestBody String payload) {
        String signature = signatureService.generatePayloadSignature(payload);
        String streamSignature = signatureService.generateStreamSignature(
                new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8))
        );

        return ResponseEntity.ok(Map.of(
                "payloadLength", payload.length(),
                "payloadSha256", signature,
                "streamSha256", streamSignature,
                "status", "SUCCESS"
        ));
    }

    @GetMapping("/inspect-class")
    public ResponseEntity<Map<String, Object>> inspectClass(@RequestParam(defaultValue = "org.apache.commons.codec.digest.DigestUtils") String className) {
        return ResponseEntity.ok(classLoaderDiagnostics.inspectClassOrigin(className));
    }
}
