package com.finflow.troubleshooting.module11.controller;

import com.finflow.troubleshooting.module11.security.JwtTokenProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final JwtTokenProvider tokenProvider;

    public AuthController(JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @PostMapping("/auth/login")
    public ResponseEntity<Map<String, Object>> login(@RequestParam String username,
                                                     @RequestParam(defaultValue = "ROLE_USER") String role) {
        String token = tokenProvider.generateToken(username, List.of(role));
        return ResponseEntity.ok(Map.of(
                "token", token,
                "tokenType", "Bearer",
                "username", username,
                "role", role
        ));
    }

    @PostMapping("/auth/expired-login")
    public ResponseEntity<Map<String, Object>> expiredLogin(@RequestParam String username) {
        // Generate an expired token (expired 10 seconds ago)
        String expiredToken = tokenProvider.generateTokenWithCustomExpiry(username, List.of("ROLE_USER"), -10000);
        return ResponseEntity.ok(Map.of(
                "token", expiredToken,
                "tokenType", "Bearer",
                "note", "This token is intentionally expired"
        ));
    }

    @GetMapping("/secure/profile")
    public ResponseEntity<Map<String, Object>> getProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "username", auth.getName(),
                "authorities", roles,
                "status", "AUTHENTICATED"
        ));
    }

    @GetMapping("/admin/dashboard")
    public ResponseEntity<Map<String, Object>> getAdminDashboard() {
        return ResponseEntity.ok(Map.of(
                "role", "ADMIN",
                "systemHealth", "OPTIMAL",
                "activeUsers", 1420
        ));
    }
}
