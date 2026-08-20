package com.finflow.chapter230.model;

public record AuthTokens(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresInSeconds
) {
}
