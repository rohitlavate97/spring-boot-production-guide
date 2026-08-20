package com.finflow.chapter220.security;

import com.finflow.chapter220.model.MerchantPrincipal;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class ApiKeyAuthenticationProvider implements AuthenticationProvider {

    // Mock API key repository for enterprise merchant accounts
    private static final Map<String, MerchantMetadata> VALID_KEYS = Map.of(
            "key_acme_admin_live", new MerchantMetadata("KEY-1", "MERCHANT_ACME", Set.of("ROLE_MERCHANT_ADMIN", "PAYMENT:WRITE", "PAYMENT:READ", "PAYMENT:REFUND")),
            "key_acme_staff_live", new MerchantMetadata("KEY-2", "MERCHANT_ACME", Set.of("ROLE_MERCHANT_STAFF", "PAYMENT:WRITE", "PAYMENT:READ")),
            "key_beta_admin_live", new MerchantMetadata("KEY-3", "MERCHANT_BETA", Set.of("ROLE_MERCHANT_ADMIN", "PAYMENT:WRITE", "PAYMENT:READ", "PAYMENT:REFUND")),
            "key_auditor_global",  new MerchantMetadata("KEY-4", "GLOBAL_AUDIT",  Set.of("ROLE_AUDITOR", "PAYMENT:READ"))
    );

    record MerchantMetadata(String keyId, String merchantId, Set<String> rolesAndPermissions) {}

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String apiKey = (String) authentication.getCredentials();

        if (apiKey == null || !VALID_KEYS.containsKey(apiKey)) {
            throw new BadCredentialsException("Invalid or unrecognized API key");
        }

        MerchantMetadata meta = VALID_KEYS.get(apiKey);
        List<SimpleGrantedAuthority> authorities = meta.rolesAndPermissions().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();

        MerchantPrincipal principal = new MerchantPrincipal(meta.keyId(), meta.merchantId(), authorities);
        return new ApiKeyAuthenticationToken(principal, apiKey, authorities);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return ApiKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
