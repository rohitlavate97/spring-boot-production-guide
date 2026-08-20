package com.finflow.chapter230.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Component
public class JwtTokenProvider {

    private static final String ISSUER = "https://auth.finflow.io";
    private static final String AUDIENCE = "finflow-api";
    private static final long ACCESS_TOKEN_EXPIRATION_MINUTES = 15;

    private final JwtKeyManager keyManager;

    public record JwtClaims(
            String subject,
            String merchantId,
            List<String> roles,
            String jwtId,
            Instant issuedAt,
            Instant expiresAt
    ) {}

    public JwtTokenProvider(JwtKeyManager keyManager) {
        this.keyManager = keyManager;
    }

    /**
     * Issues an RS256 Signed JWT Access Token.
     */
    public String generateAccessToken(String userId, String merchantId, List<String> roles) {
        Instant now = Instant.now();
        Instant exp = now.plus(ACCESS_TOKEN_EXPIRATION_MINUTES, ChronoUnit.MINUTES);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .subject(userId)
                .issuer(ISSUER)
                .audience(AUDIENCE)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(exp))
                .jwtID(UUID.randomUUID().toString())
                .claim("merchantId", merchantId)
                .claim("roles", roles)
                .build();

        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256)
                .keyID(keyManager.getKeyId())
                .build();

        SignedJWT signedJWT = new SignedJWT(header, claimsSet);
        try {
            signedJWT.sign(keyManager.getSigner());
            return signedJWT.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign JWT access token", e);
        }
    }

    /**
     * Hardened JWT Validation & Parsing:
     * 1. Rejects 'alg=none' and algorithm confusion (strictly requires RS256).
     * 2. Cryptographically verifies RS256 signature using public key.
     * 3. Enforces expiration and issuer claims.
     */
    public JwtClaims parseAndValidateToken(String rawToken) {
        try {
            SignedJWT signedJWT = SignedJWT.parse(rawToken);

            // Hardened Check 1: Strict Algorithm Enforcement
            if (!JWSAlgorithm.RS256.equals(signedJWT.getHeader().getAlgorithm())) {
                throw new SecurityException("Invalid JWT algorithm: " + signedJWT.getHeader().getAlgorithm());
            }

            // Hardened Check 2: Cryptographic Signature Verification
            if (!signedJWT.verify(keyManager.getVerifier())) {
                throw new SecurityException("JWT signature verification failed");
            }

            JWTClaimsSet claims = signedJWT.getJWTClaimsSet();
            Instant now = Instant.now();

            // Hardened Check 3: Expiration with 30s Clock Skew Leeway
            Date expDate = claims.getExpirationTime();
            if (expDate == null || expDate.toInstant().plusSeconds(30).isBefore(now)) {
                throw new SecurityException("JWT access token has expired");
            }

            // Hardened Check 4: Issuer Verification
            if (!ISSUER.equals(claims.getIssuer())) {
                throw new SecurityException("Invalid token issuer: " + claims.getIssuer());
            }

            String subject = claims.getSubject();
            String merchantId = claims.getStringClaim("merchantId");
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.getClaim("roles");
            String jti = claims.getJWTID();

            return new JwtClaims(
                    subject,
                    merchantId,
                    roles != null ? roles : List.of(),
                    jti,
                    claims.getIssueTime().toInstant(),
                    expDate.toInstant()
            );
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            throw new SecurityException("Malformed or unparseable JWT token", e);
        }
    }
}
