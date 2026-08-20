package com.finflow.chapter230.controller;

import com.finflow.chapter230.model.AuthTokens;
import com.finflow.chapter230.service.RefreshTokenRotationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final RefreshTokenRotationService rotationService;

    public AuthController(RefreshTokenRotationService rotationService) {
        this.rotationService = rotationService;
    }

    public record LoginRequest(String userId, String merchantId, List<String> roles) {}
    public record RefreshRequest(String refreshToken, List<String> roles) {}

    @PostMapping("/login")
    public ResponseEntity<AuthTokens> login(@RequestBody LoginRequest request) {
        AuthTokens tokens = rotationService.issueInitialTokens(request.userId(), request.merchantId(), request.roles());
        return ResponseEntity.ok(tokens);
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshRequest request) {
        try {
            AuthTokens tokens = rotationService.rotateTokens(request.refreshToken(), request.roles());
            return ResponseEntity.ok(tokens);
        } catch (SecurityException ex) {
            return ResponseEntity.status(401).body(Map.of("error", "Unauthorized", "message", ex.getMessage()));
        }
    }
}
