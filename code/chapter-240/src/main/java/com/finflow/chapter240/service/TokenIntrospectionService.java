package com.finflow.chapter240.service;

import com.finflow.chapter240.model.TokenIntrospectionResponse;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simulates RFC 7662 OAuth2 Token Introspection Service.
 */
@Service
public class TokenIntrospectionService {

    private final Map<String, TokenMetadata> tokenStore = new ConcurrentHashMap<>();

    public record TokenMetadata(
            String clientId,
            String subject,
            String merchantId,
            String scope,
            List<String> authorities,
            Instant expiresAt,
            boolean revoked
    ) {}

    public TokenIntrospectionService() {
        // Pre-populate with sample active opaque tokens for testing
        tokenStore.put("opaque_token_acme_pos_valid", new TokenMetadata(
                "pos-client-app-1",
                "USER-MERCHANT-ACME",
                "MERCHANT_ACME",
                "payment:write payout:execute",
                List.of("SCOPE_payment:write", "SCOPE_payout:execute", "ROLE_MERCHANT_ADMIN"),
                Instant.now().plus(1, ChronoUnit.HOURS),
                false
        ));

        tokenStore.put("opaque_token_revoked", new TokenMetadata(
                "pos-client-app-2",
                "USER-MERCHANT-REVOKED",
                "MERCHANT_ACME",
                "payment:write",
                List.of("SCOPE_payment:write"),
                Instant.now().plus(1, ChronoUnit.HOURS),
                true // Revoked!
        ));
    }

    public TokenIntrospectionResponse introspect(String token) {
        if (token == null || !tokenStore.containsKey(token)) {
            return TokenIntrospectionResponse.inactive();
        }

        TokenMetadata meta = tokenStore.get(token);
        if (meta.revoked() || meta.expiresAt().isBefore(Instant.now())) {
            return TokenIntrospectionResponse.inactive();
        }

        return new TokenIntrospectionResponse(
                true,
                meta.scope(),
                meta.clientId(),
                meta.subject(),
                meta.merchantId(),
                meta.authorities(),
                meta.expiresAt().getEpochSecond(),
                Instant.now().minus(10, ChronoUnit.MINUTES).getEpochSecond(),
                "https://auth.finflow.io"
        );
    }

    public void revoke(String token) {
        TokenMetadata meta = tokenStore.get(token);
        if (meta != null) {
            tokenStore.put(token, new TokenMetadata(
                    meta.clientId(),
                    meta.subject(),
                    meta.merchantId(),
                    meta.scope(),
                    meta.authorities(),
                    meta.expiresAt(),
                    true
            ));
        }
    }
}
