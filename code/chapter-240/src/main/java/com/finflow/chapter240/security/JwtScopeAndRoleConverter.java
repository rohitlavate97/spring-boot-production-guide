package com.finflow.chapter240.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class JwtScopeAndRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        // 1. Convert OAuth2 scopes (scope or scp claim) to SCOPE_ authorities
        Object scopeClaim = jwt.getClaims().get("scope");
        if (scopeClaim instanceof String scopes) {
            for (String scope : scopes.split(" ")) {
                if (!scope.isBlank()) {
                    authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope.trim()));
                }
            }
        } else if (scopeClaim instanceof Collection<?> scopeList) {
            for (Object scope : scopeList) {
                authorities.add(new SimpleGrantedAuthority("SCOPE_" + scope.toString().trim()));
            }
        }

        // 2. Convert custom finflow_roles to ROLE_ authorities
        Object rolesClaim = jwt.getClaims().get("finflow_roles");
        if (rolesClaim instanceof Collection<?> rolesList) {
            for (Object role : rolesList) {
                String roleStr = role.toString().trim();
                String authorityName = roleStr.startsWith("ROLE_") ? roleStr : "ROLE_" + roleStr;
                authorities.add(new SimpleGrantedAuthority(authorityName));
            }
        }

        // 3. Build OAuth2MerchantPrincipal
        String merchantId = jwt.getClaimAsString("merchant_id");
        String clientId = jwt.getClaimAsString("client_id");
        if (clientId == null) {
            clientId = jwt.getClaimAsString("azp");
        }

        OAuth2MerchantPrincipal principal = new OAuth2MerchantPrincipal(
                jwt.getSubject(),
                merchantId,
                clientId,
                authorities
        );

        return new JwtAuthenticationToken(jwt, authorities, principal.getSubject());
    }
}
