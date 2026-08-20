package com.finflow.chapter230.service;

import com.finflow.chapter230.model.AuthTokens;
import com.finflow.chapter230.model.RefreshTokenRecord;
import com.finflow.chapter230.security.JwtTokenProvider;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RefreshTokenRotationService {

    private static final long REFRESH_TOKEN_EXPIRATION_DAYS = 7;
    private final Map<String, RefreshTokenRecord> tokenStore = new ConcurrentHashMap<>();
    private final JwtTokenProvider jwtTokenProvider;

    public RefreshTokenRotationService(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * Issues an initial token pair upon successful authentication.
     */
    public AuthTokens issueInitialTokens(String userId, String merchantId, List<String> roles) {
        String familyId = UUID.randomUUID().toString();
        String rawRefreshToken = UUID.randomUUID().toString();

        RefreshTokenRecord record = new RefreshTokenRecord(
                rawRefreshToken,
                userId,
                merchantId,
                familyId,
                Instant.now().plus(REFRESH_TOKEN_EXPIRATION_DAYS, ChronoUnit.DAYS)
        );
        tokenStore.put(rawRefreshToken, record);

        String accessToken = jwtTokenProvider.generateAccessToken(userId, merchantId, roles);
        return new AuthTokens(accessToken, rawRefreshToken, "Bearer", 900);
    }

    /**
     * Executes Refresh Token Rotation (RTR) with Automatic Replay / Reuse Detection.
     */
    public synchronized AuthTokens rotateTokens(String rawRefreshToken, List<String> roles) {
        RefreshTokenRecord existing = tokenStore.get(rawRefreshToken);

        if (existing == null) {
            throw new SecurityException("Invalid or unknown refresh token");
        }

        if (existing.isRevoked()) {
            throw new SecurityException("Refresh token is revoked");
        }

        // CRITICAL: Replay Attack Detection!
        // If an already-used token is submitted again, a malicious party may have intercepted it!
        if (existing.isUsed()) {
            // Invalidate ALL tokens in this family immediately!
            revokeTokenFamily(existing.getFamilyId());
            throw new SecurityException("Security Alert: Refresh token reuse detected! Revoking token family: " + existing.getFamilyId());
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            throw new SecurityException("Refresh token has expired");
        }

        // Mark the consumed token as used
        existing.setUsed(true);

        // Issue a NEW refresh token preserving the same family ID
        String newRefreshToken = UUID.randomUUID().toString();
        RefreshTokenRecord newRecord = new RefreshTokenRecord(
                newRefreshToken,
                existing.getUserId(),
                existing.getMerchantId(),
                existing.getFamilyId(),
                Instant.now().plus(REFRESH_TOKEN_EXPIRATION_DAYS, ChronoUnit.DAYS)
        );
        tokenStore.put(newRefreshToken, newRecord);

        // Issue new short-lived access token
        String newAccessToken = jwtTokenProvider.generateAccessToken(
                existing.getUserId(),
                existing.getMerchantId(),
                roles
        );

        return new AuthTokens(newAccessToken, newRefreshToken, "Bearer", 900);
    }

    /**
     * Revokes all active refresh tokens associated with the compromised family.
     */
    public void revokeTokenFamily(String familyId) {
        for (RefreshTokenRecord record : tokenStore.values()) {
            if (record.getFamilyId().equals(familyId)) {
                record.setRevoked(true);
            }
        }
    }

    public boolean isTokenRevoked(String rawToken) {
        RefreshTokenRecord record = tokenStore.get(rawToken);
        return record != null && record.isRevoked();
    }
}
