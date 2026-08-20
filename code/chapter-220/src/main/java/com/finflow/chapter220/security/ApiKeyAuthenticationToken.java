package com.finflow.chapter220.security;

import com.finflow.chapter220.model.MerchantPrincipal;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class ApiKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final String apiKey;
    private final MerchantPrincipal principal;

    // Unauthenticated token constructor (created by Filter)
    public ApiKeyAuthenticationToken(String apiKey) {
        super(null);
        this.apiKey = apiKey;
        this.principal = null;
        setAuthenticated(false);
    }

    // Authenticated token constructor (created by AuthenticationProvider)
    public ApiKeyAuthenticationToken(MerchantPrincipal principal, String apiKey, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.apiKey = apiKey;
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return apiKey;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
