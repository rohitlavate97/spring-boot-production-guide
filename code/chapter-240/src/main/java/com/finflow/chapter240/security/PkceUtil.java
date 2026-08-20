package com.finflow.chapter240.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * RFC 7636 Proof Key for Code Exchange (PKCE) Utility.
 */
public final class PkceUtil {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private PkceUtil() {}

    /**
     * Generates a high-entropy cryptographic code verifier string (43 to 128 chars).
     */
    public static String generateCodeVerifier() {
        byte[] codeVerifierBytes = new byte[64];
        SECURE_RANDOM.nextBytes(codeVerifierBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(codeVerifierBytes);
    }

    /**
     * Computes the S256 Code Challenge from the code verifier:
     * code_challenge = BASE64URL-ENCODE(SHA256(ASCII(code_verifier)))
     */
    public static String generateCodeChallenge(String codeVerifier) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(codeVerifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Validates that the provided code_verifier matches the original code_challenge under S256.
     */
    public static boolean verifyCodeChallenge(String codeVerifier, String codeChallenge) {
        if (codeVerifier == null || codeChallenge == null) {
            return false;
        }
        String calculatedChallenge = generateCodeChallenge(codeVerifier);
        return MessageDigest.isEqual(
                calculatedChallenge.getBytes(StandardCharsets.UTF_8),
                codeChallenge.getBytes(StandardCharsets.UTF_8)
        );
    }
}
